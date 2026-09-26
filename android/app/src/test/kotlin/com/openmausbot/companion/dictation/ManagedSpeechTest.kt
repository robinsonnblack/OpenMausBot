package com.openmausbot.companion.dictation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ManagedSpeechTest {
    private fun pcm(ms: Int, value: Int = 0) = ByteArray(ms * 32).apply {
        for (i in indices step 2) { this[i] = value.toByte(); this[i + 1] = (value shr 8).toByte() }
    }
    @Test fun pauseUsesConfiguredLengthAndResetsWhenSpeechResumes() {
        val pause = SpeechPause("silence", 500)
        assertFalse(pause.push(pcm(1000), 32000))
        assertFalse(pause.push(pcm(200, 3000), 6400))
        assertFalse(pause.push(pcm(400), 12800))
        assertFalse(pause.push(pcm(200, 3000), 6400))
        assertFalse(pause.push(pcm(499), 499 * 32))
        assertTrue(pause.push(pcm(1), 32))
    }
    @Test fun manualRecordingDoesNotEndAtAnyPause() {
        val pause = SpeechPause("manual", 1)
        assertFalse(pause.push(pcm(200, 3000), 6400))
        assertTrue(pause.hasSpeech)
        assertFalse(pause.push(pcm(120000), 120000 * 32))
    }
    @Test fun oldSettingsMigrateAndInvalidActionsAreRejected() {
        val cfg = com.openmausbot.companion.core.CompanionJson.decodeFromString<SttConfig>("""{"provider":"android"}""")
        assertEquals("manual", cfg.stopMode); assertEquals("insert", cfg.afterAction)
        for (ms in listOf(0, -1)) assertFails { cfg.copy(silenceMs = ms).validated() }
        assertFails { cfg.copy(afterAction = "unknown").validated() }
    }
    @Test fun cloudProcessesLongRecordingsInOrderAndFlushesTheLastChunk() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val finish = CompletableDeferred<Unit>()
        val capture = object : PcmInput {
            override fun finish() { finish.complete(Unit) }
            override suspend fun run(request: RecognitionRequest, onReady: suspend () -> Unit, onData: suspend (ByteArray, Int) -> Unit) {
                onReady()
                for (i in 0..2) { val audio = pcm(28000, 3000); onData(audio, audio.size); yield() }
                val tail = pcm(200, 3000); onData(tail, tail.size)
                finish.await()
            }
        }
        var calls = 0
        val engine = ManagedCloudSpeechEngine(SttConfig("openrouter"), capture, transcribe = { _, audio ->
            assertTrue(audio.size <= 28000 * 32); "part${++calls}"
        }, validate = {})
        val events = mutableListOf<String>()
        try {
            engine.start(RecognitionRequest("de-DE", false), object : SpeechEngine.Listener {
                override fun onReady() { events += "ready" }
                override fun onProcessing() { events += "processing" }
                override fun onPartial(text: String) { events += "partial:$text" }
                override fun onFinal(text: String) { events += "final:$text" }
                override fun onError(kind: SpeechEngine.ErrorKind) { fail("Unexpected error $kind") }
            })
            runCurrent(); assertFalse(events.any { it.startsWith("final:") })
            engine.finish(); advanceUntilIdle()
            assertEquals(4, calls)
            assertEquals("final:part1 part2 part3 part4", events.last())
            assertTrue(events.indexOf("processing") < events.lastIndex)
        } finally { engine.destroy(); Dispatchers.resetMain() }
    }
}
