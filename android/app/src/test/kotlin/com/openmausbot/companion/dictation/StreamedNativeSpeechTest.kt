package com.openmausbot.companion.dictation

import android.os.Bundle
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSpeechRecognizer
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class StreamedNativeSpeechTest {
    @Test fun nativeSegmentsAndPausesDoNotEndRecordingBeforeTheAppClosesInput() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val stop = CompletableDeferred<Unit>()
        val input = object : PcmInput {
            override fun finish() { stop.complete(Unit) }
            override suspend fun run(request: RecognitionRequest, onReady: suspend () -> Unit, onData: suspend (ByteArray, Int) -> Unit) {
                onReady(); onData(ByteArray(320), 320); stop.await()
            }
        }
        val events = mutableListOf<String>()
        val engine = StreamedAndroidSpeechEngine(RuntimeEnvironment.getApplication(), input)
        try {
            engine.start(RecognitionRequest("de-DE", false), object : SpeechEngine.Listener {
                override fun onReady() { events += "ready" }
                override fun onProcessing() { events += "processing" }
                override fun onPartial(text: String) { events += "partial:$text" }
                override fun onFinal(text: String) { events += "final:$text" }
                override fun onError(kind: SpeechEngine.ErrorKind) { fail("Unexpected $kind") }
            })
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val shadow = Shadows.shadowOf(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            val state = org.robolectric.util.ReflectionHelpers.callInstanceMethod<Any>(shadow, "getState")
            val callback = org.robolectric.util.ReflectionHelpers.getField<android.speech.RecognitionListener>(state, "recognitionListener")
            assertEquals(RecognizerIntent.EXTRA_AUDIO_SOURCE, shadow.lastRecognizerIntent.getStringExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION))
            shadow.triggerOnReadyForSpeech(Bundle())
            assertTrue(events.contains("ready"))
            fun phrase(text: String) = Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) }
            callback.onSegmentResults(phrase("eins")); shadow.triggerOnEndOfSpeech()
            shadow.triggerOnPartialResults(phrase("zwei")); callback.onSegmentResults(phrase("zwei"))
            assertFalse(events.contains("processing")); assertFalse(events.any { it.startsWith("final:") })
            assertEquals("partial:eins zwei", events.last())
            engine.finish(); runCurrent()
            assertTrue(events.contains("processing"))
            callback.onEndOfSegmentedSession()
            assertEquals("final:eins zwei", events.last())
        } finally { engine.destroy(); Dispatchers.resetMain() }
    }
}
