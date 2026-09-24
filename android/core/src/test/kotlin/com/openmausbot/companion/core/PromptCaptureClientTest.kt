package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class PromptCaptureClientTest {
    @Test fun readsCapturedRequestWithoutDiscardingInputOrUsage() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "device-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"records":[{"id":"one","threadId":"thread-1","provider":"codex","kind":"api-request","sentAt":"2026-09-24T20:00:00Z","status":"completed","body":{"input":[{"role":"user","content":"hello"}]},"usage":{"input":120,"cached":80,"uncached":40,"output":7}}]}"""))
            val capture = client.promptCaptures("thread-1").single()
            assertEquals("codex", capture.provider)
            assertEquals(80, capture.usage?.cached)
            assertEquals(40, capture.usage?.uncached)
            assertEquals("""{"input":[{"role":"user","content":"hello"}]}""", capture.body.toString())
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/threads/thread-1/prompt-inspector", request.path)
        } finally { server.shutdown() }
    }
}
