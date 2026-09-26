package com.openmausbot.companion.dictation

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

fun sttWav(pcm: ByteArray): ByteArray {
    require(pcm.isNotEmpty() && pcm.size % 2 == 0) { "No speech was recorded." }
    val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    h.put("RIFF".toByteArray()).putInt(pcm.size + 36).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        .putInt(16000).putInt(32000).putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size)
    return h.array() + pcm
}
class SttCloud(private val http: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(90, TimeUnit.SECONDS).build()) {
    fun request(cfg: SttConfig, pcm: ByteArray): Request {
        val provider = STT_PROVIDERS.firstOrNull { it.id == cfg.provider && it.id != "android" } ?: error("Choose an STT provider.")
        val profile = cfg.profiles[provider.id] ?: SttProfile()
        require(profile.key.isNotBlank() || provider.id == "compatible") { "Add an API key or import it from the desktop." }
        val lang = cfg.language.takeUnless { it == "auto" }?.also { require(Regex("[a-z]{2,3}(-[A-Za-z0-9]{2,8})*").matches(it)) { "Choose a valid speech language." } }
        val wav = sttWav(pcm)
        val model = profile.model.ifBlank { provider.model }
        val builder = Request.Builder()
        if (provider.id == "deepgram") {
            val url = "https://api.deepgram.com/v1/listen".toHttpUrl().newBuilder().addQueryParameter("model", model).addQueryParameter("smart_format", "true")
                .addQueryParameter(if (lang == null) "detect_language" else "language", lang?.substringBefore('-') ?: "true").build()
            return builder.url(url).header("Authorization", "Token ${profile.key}").post(wav.toRequestBody("audio/wav".toMediaType())).build()
        }
        val base = when(provider.id) { "openrouter" -> "https://openrouter.ai/api/v1"; "openai" -> "https://api.openai.com/v1"; "groq" -> "https://api.groq.com/openai/v1"; else -> profile.endpoint }
        require(base.isNotBlank()) { "Enter the STT service URL." }
        val url = base.toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null &&
            (url.isHttps || url.host in listOf("localhost", "127.0.0.1", "::1"))) { "Use HTTPS for the STT service." }
        if (provider.id == "azure") {
            require(url.host.endsWith(".cognitiveservices.azure.com") || url.host.endsWith(".stt.speech.microsoft.com")) { "Enter your Azure Speech endpoint." }
            require(lang != null) { "Azure needs a language, for example de-DE." }
            val path = if(url.host.endsWith(".cognitiveservices.azure.com")) "/stt/speech/recognition/conversation/cognitiveservices/v1" else "/speech/recognition/conversation/cognitiveservices/v1"
            return builder.url(url.newBuilder().encodedPath(path).addQueryParameter("language", lang).addQueryParameter("format", "simple").build())
                .header("Ocp-Apim-Subscription-Key", profile.key).post(wav.toRequestBody("audio/wav; codecs=\"audio/pcm\"; samplerate=16000".toMediaType())).build()
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", model).addFormDataPart("response_format", "json")
        lang?.let { body.addFormDataPart("language", it.substringBefore('-')) }
        if(profile.key.isNotBlank()) builder.header("Authorization", "Bearer ${profile.key}")
        return builder.url(url.newBuilder().encodedPath(url.encodedPath.trimEnd('/').removeSuffix("/audio/transcriptions") + "/audio/transcriptions").build()).post(body.build()).build()
    }
    @OptIn(InternalCoroutinesApi::class)
    suspend fun transcribe(cfg: SttConfig, pcm: ByteArray): String = withContext(Dispatchers.IO) {
        val call = http.newCall(request(cfg, pcm))
        val registration = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { call.cancel() }
        try {
            call.execute().use { response ->
                if(!response.isSuccessful) error(when(response.code) { 401,403 -> "The STT provider rejected the API key."; 429 -> "STT limit reached or insufficient credit."; else -> "STT provider returned HTTP ${response.code}." })
                val body = response.body ?: error("The STT provider returned an empty response.")
                val source = body.source(); source.request(1_000_001)
                require(source.buffer.size <= 1_000_000) { "STT response is too large." }
                val json = runCatching { Json.parseToJsonElement(source.readUtf8()).jsonObject }.getOrElse { error("The STT provider returned an invalid response.") }
                val text = when(cfg.provider) {
                    "deepgram" -> json["results"]?.jsonObject?.get("channels")?.jsonArray?.firstOrNull()?.jsonObject?.get("alternatives")?.jsonArray?.firstOrNull()?.jsonObject?.get("transcript")?.jsonPrimitive?.content
                    "azure" -> json["DisplayText"]?.jsonPrimitive?.content
                    else -> json["text"]?.jsonPrimitive?.content
                }?.trim()
                require(!text.isNullOrBlank()) { "No speech recognized. Try again." }
                text
            }
        } catch(cancellation: CancellationException) { throw cancellation }
        catch(error: java.io.IOException) { error("Could not reach the STT provider. Check your connection.") }
        finally { registration.dispose() }
    }
}
internal class ConfiguredSpeechEngineFactory(private val context: Context) : SpeechEngineFactory {
    override fun openers(): List<EngineOpener> {
        val config = SttStore(context).load()
        return if(config.provider == "android") AndroidSpeechEngineFactory(context).openers()
        else listOf(EngineOpener(false) { CloudSpeechEngine(config) })
    }
}
internal class CloudSpeechEngine(private val config: SttConfig) : SpeechEngine {
    override val isOnDevice = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var recording = false
    @Volatile private var cancelled = false
    private var recorder: AudioRecord? = null
    override fun start(request: RecognitionRequest, listener: SpeechEngine.Listener) {
        recording = true
        scope.launch {
            try {
                SttCloud().request(config, ByteArray(320))
                val pcm = withContext(Dispatchers.IO) {
                    val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
                    val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
                    recorder = audio
                    try {
                        check(audio.state == AudioRecord.STATE_INITIALIZED) { "Could not start the microphone." }
                        audio.startRecording()
                        check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Could not start the microphone." }
                        withContext(Dispatchers.Main) { listener.onReady() }
                        val output = ByteArrayOutputStream(); val buffer = ByteArray(size)
                        while(recording && output.size() < 16000 * 2 * 60) {
                            ensureActive()
                            val read = audio.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                            if(read > 0) output.write(buffer, 0, minOf(read, 16000 * 2 * 60 - output.size()))
                            else if(read < 0 && recording) error("Microphone recording failed ($read).") else delay(10)
                        }
                        output.toByteArray()
                    } finally { recorder = null; runCatching { audio.stop() }; audio.release() }
                }
                if(!cancelled) {
                    listener.onProcessing()
                    try { listener.onFinal(SttCloud().transcribe(config, pcm)) } finally { pcm.fill(0) }
                } else pcm.fill(0)
            } catch(cancellation: CancellationException) { throw cancellation }
            catch(failure: Exception) { if(!cancelled) listener.onFailure(failure.message ?: "Transcription failed.") }
        }
    }
    override fun finish() { recording = false }
    override fun cancel() { cancelled = true; recording = false; scope.cancel(); runCatching { recorder?.stop() } }
    override fun destroy() = cancel()
}
