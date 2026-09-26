package com.openmausbot.companion.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.openmausbot.companion.core.CompanionJson
import com.openmausbot.companion.storage.KeystoreTokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable data class PhoneVoiceConfig(val provider: String = "android", val key: String = "",
    val voice: String = "", val endpoint: String = "", val model: String = "")

class PhoneVoiceStore(context: Context) {
    private val prefs by lazy { KeystoreTokenStore.openPrefs(context.applicationContext) }
    fun load(): PhoneVoiceConfig = prefs.getString("tts.settings", null)?.let { CompanionJson.decodeFromString<PhoneVoiceConfig>(it) } ?: PhoneVoiceConfig()
    fun save(value: PhoneVoiceConfig) {
        require(value.provider in listOf("android", "elevenlabs", "fish", "xai", "chatterbox"))
        check(prefs.edit().putString("tts.settings", CompanionJson.encodeToString(value)).commit())
    }
}

/** All synthesis and playback belong to this phone. No audio request to the paired PC. */
class PhoneSpeech(context: Context) {
    private val context = context.applicationContext
    val store = PhoneVoiceStore(context)
    private val player = CallSpeaker(context)
    private val focus = AudioFocusGate(context)
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .callTimeout(60, TimeUnit.SECONDS).build()
    private var active: Deferred<Boolean>? = null
    private var engine: TextToSpeech? = null
    private var initialized: CompletableDeferred<Unit>? = null
    private var nativeDone: CompletableDeferred<Boolean>? = null
    private val _speaking = MutableStateFlow(false)
    val isSpeaking = _speaking.asStateFlow()
    private val _messageId = MutableStateFlow<String?>(null)
    val messageId = _messageId.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _errorMessageId = MutableStateFlow<String?>(null)
    val errorMessageId = _errorMessageId.asStateFlow()

    fun stop() { active?.cancel(); player.stop(); engine?.stop(); nativeDone?.complete(false); focus.abandon(); _speaking.value = false; _messageId.value = null }

    suspend fun speak(text: String, voice: String?, messageId: String? = null): Boolean = coroutineScope {
        stop(); _error.value = null; _errorMessageId.value = null; _speaking.value = true; _messageId.value = messageId
        val task = async(start = CoroutineStart.LAZY) {
            val cfg = withContext(Dispatchers.IO) { store.load() }
            val cleaned = speechText(text)
            if (cfg.provider == "android") speakNative(cleaned, voice?.takeIf { it.isNotBlank() } ?: cfg.voice)
            else {
                val selected = voice?.takeIf { it.isNotBlank() } ?: cfg.voice
                require(selected.isNotBlank()) { context.getString(com.openmausbot.companion.R.string.phone_voice_required) }
                val parts = speechParts(cleaned)
                for (part in parts) {
                    ensureActive()
                    val clip = synthesize(cfg, part, selected)
                    if (!player.speak(listOf(clip))) throw IOException(context.getString(com.openmausbot.companion.R.string.phone_voice_failed))
                }
                true
            }
        }
        active = task
        try { task.await() }
        catch (e: CancellationException) { false }
        catch (e: Exception) { if (active === task) { _errorMessageId.value = messageId; _error.value = e.message ?: context.getString(com.openmausbot.companion.R.string.phone_voice_failed) }; false }
        finally { if (active === task) { active = null; _speaking.value = false; _messageId.value = null; focus.abandon() } }
    }

    private suspend fun speakNative(text: String, voice: String): Boolean {
        if (engine == null) {
            val ready = CompletableDeferred<Unit>(); initialized = ready
            engine = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) ready.complete(Unit) else ready.completeExceptionally(IOException(context.getString(com.openmausbot.companion.R.string.phone_voice_failed))) }
        }
        withTimeout(15_000) { initialized!!.await() }
        val tts = engine!!
        val language = context.resources.configuration.locales[0]
        if (tts.setLanguage(language) < 0) throw IOException(context.getString(com.openmausbot.companion.R.string.phone_voice_language_missing))
        tts.voices?.find { it.name == voice }?.let { tts.voice = it }
        if (!focus.request(onInterrupted = { stop() })) throw IOException(context.getString(com.openmausbot.companion.R.string.phone_voice_failed))
        // Android engines impose an input cap. Split before it, never silently truncate.
        for (part in speechParts(text, minOf(3000, TextToSpeech.getMaxSpeechInputLength() - 1))) {
            val utteranceId = UUID.randomUUID().toString()
            val piece = CompletableDeferred<Boolean>(); nativeDone = piece
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { if (id == utteranceId) piece.complete(true) }
                @Deprecated("Android compatibility") override fun onError(id: String?) { if (id == utteranceId) piece.complete(false) }
            })
            if (tts.speak(part, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR || !piece.await()) throw IOException(context.getString(com.openmausbot.companion.R.string.phone_voice_failed))
        }
        return true
    }

    private suspend fun synthesize(cfg: PhoneVoiceConfig, text: String, voice: String): ByteArray {
        val request = phoneVoiceRequest(cfg, text, voice)
        val call = client.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(IOException(context.getString(com.openmausbot.companion.R.string.phone_voice_failed))) }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!it.isSuccessful) throw IOException(context.getString(com.openmausbot.companion.R.string.phone_voice_provider_error, it.code))
                            val stream = it.body?.byteStream() ?: throw IOException(context.getString(com.openmausbot.companion.R.string.phone_voice_failed))
                            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                            while (true) { val count = stream.read(buffer); if (count < 0) break; if (output.size() + count > 20_000_000) throw IOException(context.getString(com.openmausbot.companion.R.string.phone_voice_failed)); output.write(buffer, 0, count) }
                            if (continuation.isActive) continuation.resume(output.toByteArray())
                        } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                    }
                }
            })
        }
    }
}

internal fun phoneVoiceRequest(cfg: PhoneVoiceConfig, text: String, voice: String): Request {
    val builder = Request.Builder().header("Accept", "audio/mpeg")
    val body = buildJsonObject {
        when (cfg.provider) {
            "elevenlabs" -> { builder.url("https://api.elevenlabs.io/v1/text-to-speech/${java.net.URLEncoder.encode(voice, "UTF-8")}?output_format=mp3_44100_64").header("xi-api-key", cfg.key); put("text", text); put("model_id", cfg.model.ifBlank { "eleven_flash_v2_5" }) }
            "fish" -> { builder.url("https://api.fish.audio/v1/tts").header("Authorization", "Bearer ${cfg.key}").header("model", cfg.model.ifBlank { "s2.1-pro" }); put("text", text); put("reference_id", voice); put("format", "mp3"); put("sample_rate", 44100); put("mp3_bitrate", 64); put("latency", "normal") }
            "xai" -> { builder.url("https://api.x.ai/v1/tts").header("Authorization", "Bearer ${cfg.key}"); put("text", text); put("voice_id", voice); put("language", "auto"); put("output_format", buildJsonObject { put("codec", "mp3") }) }
            "chatterbox" -> {
                val url = cfg.endpoint.trimEnd('/').toHttpUrl()
                require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null &&
                    (url.isHttps || url.host.endsWith(".ts.net") || isTailnetHost(url.host) || url.host in listOf("127.0.0.1", "localhost"))) { "Use HTTPS or a Tailscale address for the voice server." }
                builder.url(url.newBuilder().addPathSegments(if (url.encodedPath.trimEnd('/').endsWith("/v1")) "audio/speech" else "v1/audio/speech").build()).header("Accept", "audio/wav")
                put("input", text); put("voice", voice); put("model", cfg.model.ifBlank { "chatterbox-turbo" }); put("response_format", "wav")
            }
            else -> error("Unknown voice provider")
        }
    }
    return builder.post(body.toString().toRequestBody("application/json".toMediaType())).build()
}
internal fun speechText(text: String): String = text.replace(Regex("```[\\s\\S]*?```"), "")
    .replace(Regex("!?\\[([^]]+)]\\([^)]+\\)"), "$1").replace(Regex("[*_`#]"), "").trim()
internal fun speechParts(text: String, maximum: Int = 900): List<String> {
    val result = mutableListOf<String>(); var remaining = text.trim()
    while (remaining.isNotEmpty()) {
        val prefix = remaining.take(maximum)
        val sentence = Regex("[.!?](?:\\s|$)").findAll(prefix).lastOrNull()?.range?.last?.plus(1)
        val end = if (remaining.length <= maximum) remaining.length else sentence ?: prefix.lastIndexOf(' ').takeIf { it > 0 } ?: prefix.length
        result += remaining.take(end).trim(); remaining = remaining.drop(end).trimStart()
    }
    return result.filter { it.isNotEmpty() }
}

internal fun isTailnetHost(host: String): Boolean {
    val parts = host.split('.').map { it.toIntOrNull() }
    return parts.size == 4 && parts[0] == 100 && (parts[1] ?: -1) in 64..127 && parts.drop(2).all { it != null && it in 0..255 }
}
