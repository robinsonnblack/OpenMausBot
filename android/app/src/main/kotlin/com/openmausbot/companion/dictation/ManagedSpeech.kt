package com.openmausbot.companion.dictation

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.math.sqrt

/** Uses sample time, not callback/wall-clock timing; silence before speech never stops capture. */
internal class SpeechPause(private val mode: String, private val milliseconds: Int) {
    private var voiced = 0.0
    private var silent = 0.0
    val hasSpeech: Boolean get() = voiced >= 120
    fun push(pcm: ByteArray, count: Int): Boolean {
        if (count < 2) return false
        var squares = 0.0
        for (i in 0 until count - 1 step 2) {
            val sample = ((pcm[i].toInt() and 255) or (pcm[i + 1].toInt() shl 8)).toShort().toDouble() / 32768.0
            squares += sample * sample
        }
        val duration = (count / 2) * 1000.0 / 16000
        if (sqrt(squares / (count / 2)) > .006) { voiced += duration; silent = 0.0 } else silent += duration
        return mode == "silence" && hasSpeech && silent >= milliseconds
    }
}

/** One app-owned PCM source shared by native streamed recognition and cloud recording. */
internal interface PcmInput {
    fun finish()
    suspend fun run(request: RecognitionRequest, onReady: suspend () -> Unit, onData: suspend (ByteArray, Int) -> Unit)
}
internal class SpeechCapture : PcmInput {
    @Volatile private var recording = true
    @Volatile private var recorder: AudioRecord? = null
    override fun finish() { recording = false; runCatching { recorder?.stop() } }
    override suspend fun run(request: RecognitionRequest, onReady: suspend () -> Unit, onData: suspend (ByteArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        val size = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val audio = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        recorder = audio
        try {
            check(audio.state == AudioRecord.STATE_INITIALIZED) { "Could not start the microphone." }
            if (!recording) return@withContext
            audio.startRecording()
            check(audio.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Could not start the microphone." }
            onReady()
            val pause = SpeechPause(request.stopMode, request.silenceMs)
            val buffer = ByteArray(size)
            try {
                while (recording) {
                    ensureActive()
                    val count = audio.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                    if (count > 0) {
                        onData(buffer, count)
                        if (pause.push(buffer, count)) recording = false
                    } else if (count < 0 && recording) error("Microphone recording failed ($count).") else delay(10)
                }
                check(pause.hasSpeech) { "No speech was recorded." }
            } finally { buffer.fill(0) }
        } finally { recorder = null; runCatching { audio.stop() }; audio.release() }
    }
}

/** Android 13+ segmented input: the recognizer ends when this app closes the audio stream. */
@android.annotation.TargetApi(33)
internal class StreamedAndroidSpeechEngine(context: Context, private val capture: PcmInput = SpeechCapture()) : SpeechEngine {
    override val isOnDevice = false
    // Google's service still owns focus. Competing client focus cancels its startup.
    override val managesAudioFocus = true
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pipe: Array<ParcelFileDescriptor>? = null
    private var output: ParcelFileDescriptor.AutoCloseOutputStream? = null
    private var listener: SpeechEngine.Listener? = null
    private var text = ""
    private var ended = false
    private var inputClosed = false
    private var audioReady = false
    private var serviceReady = false
    private fun ready() { if (audioReady && serviceReady && !ended) listener?.onReady() }
    override fun start(request: RecognitionRequest, listener: SpeechEngine.Listener) {
        this.listener = listener
        val descriptors = ParcelFileDescriptor.createPipe(); pipe = descriptors
        val writer = ParcelFileDescriptor.AutoCloseOutputStream(descriptors[1]); output = writer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { serviceReady = true; ready() }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit // End of a phrase is not the end of this recording.
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                result(partialResults)?.let { if (!ended) listener.onPartial(joinSpeech(text, it)) }
            }
            override fun onSegmentResults(segmentResults: Bundle) {
                result(segmentResults)?.let { text = joinSpeech(text, it); if (!ended) listener.onPartial(text) }
            }
            override fun onEndOfSegmentedSession() {
                if (ended) return
                if (!inputClosed) listener.onFailure("Android recognition ended before recording stopped. This speech service does not support continuous input.")
                else { ended = true; listener.onFinal(text) }
            }
            override fun onResults(results: Bundle?) {
                if (ended) return
                if (!inputClosed) listener.onFailure("Android recognition ended before recording stopped. This speech service does not support continuous input.")
                else { ended = true; listener.onFinal(joinSpeech(text, result(results).orEmpty())) }
            }
            override fun onError(error: Int) {
                if (ended) return
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) listener.onError(SpeechEngine.ErrorKind.PERMISSION)
                else listener.onFailure("Android speech recognition failed (code $error).")
            }
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, descriptors[0])
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        })
        scope.launch {
            try {
                capture.run(request, onReady = { withContext(Dispatchers.Main) { audioReady = true; ready() } }, onData = { buffer, count -> writer.write(buffer, 0, count) })
                if (!ended) {
                    inputClosed = true
                    listener.onProcessing()
                    withContext(Dispatchers.IO) { writer.close() }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { if (!ended) listener.onFailure(failure.message ?: "Transcription failed.") }
        }
    }
    override fun finish() { capture.finish() }
    override fun cancel() {
        ended = true; listener = null; capture.finish(); scope.cancel()
        runCatching { output?.close() }; pipe?.forEach { runCatching { it.close() } }
        runCatching { recognizer.cancel() }
    }
    override fun destroy() { cancel(); recognizer.destroy() }
    private fun result(bundle: Bundle?) = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

internal fun joinSpeech(before: String, next: String) = listOf(before.trim(), next.trim()).filter { it.isNotEmpty() }.joinToString(" ")

/** Bounded audio chunks and an ordered queue allow long manual recordings without keeping all PCM. */
internal class ManagedCloudSpeechEngine(private val config: SttConfig, private val capture: PcmInput = SpeechCapture(),
    private val transcribe: suspend (SttConfig, ByteArray) -> String = { cfg, pcm -> SttCloud().transcribe(cfg, pcm) },
    private val validate: (SttConfig) -> Unit = { SttCloud().request(it, ByteArray(320)); Unit }) : SpeechEngine {
    override val isOnDevice = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cancelled = false
    override fun start(request: RecognitionRequest, listener: SpeechEngine.Listener) {
        val chunks = Channel<ByteArray>(4, onUndeliveredElement = { it.fill(0) })
        scope.launch {
            var text = ""
            try {
                for (pcm in chunks) {
                    try { text = joinSpeech(text, transcribe(config, pcm)); if (!cancelled) listener.onPartial(text) }
                    finally { pcm.fill(0) }
                }
                if (!cancelled) listener.onFinal(text)
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { if (!cancelled) listener.onFailure(failure.message ?: "Transcription failed.") }
            finally { chunks.cancel() }
        }
        scope.launch {
            try {
                validate(config) // Validate before opening the microphone.
                capture.run(request, onReady = { withContext(Dispatchers.Main) { listener.onReady() } }, onData = chunkWriter(chunks))
                if (!cancelled) listener.onProcessing()
                flush()
                chunks.close()
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { chunks.close(failure) }
        }
    }
    private fun chunkWriter(chunks: Channel<ByteArray>): suspend (ByteArray, Int) -> Unit {
        val buffer = ByteArrayOutputStream()
        // The final buffer is flushed by capture completion, not by a silence-triggered stop.
        pendingBuffer = buffer; pendingChunks = chunks
        return { bytes, count ->
            buffer.write(bytes, 0, count)
            if (buffer.size() >= 16000 * 2 * 28) {
                val pcm = buffer.toByteArray(); buffer.reset()
                if (!chunks.trySend(pcm).isSuccess) { pcm.fill(0); error("Transcription cannot keep up. Choose a faster service.") }
            }
        }
    }
    private var pendingBuffer: ByteArrayOutputStream? = null
    private var pendingChunks: Channel<ByteArray>? = null
    private suspend fun flush() {
        val pcm = pendingBuffer?.toByteArray(); pendingBuffer?.reset()
        if (pcm != null && pcm.isNotEmpty()) pendingChunks?.send(pcm)
    }
    override fun finish() { capture.finish() }
    override fun cancel() { cancelled = true; capture.finish(); scope.cancel(); pendingChunks?.cancel(); pendingBuffer?.reset() }
    override fun destroy() = cancel()
}
