package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ThreadSettingsClientTest {
    @Test
    fun savesThreadLimitsAndClearsOptionalRetention() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"threads":{"maxConcurrentPerBot":4}}"""))
            val saved = client.updateThreadSettings(ThreadSettings(maxConcurrentPerBot = 4))
            assertEquals(ThreadSettings(maxConcurrentPerBot = 4), saved.threads)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/config", request.path)
            assertEquals("""{"threads":{"maxConcurrentPerBot":4,"eventLogMaxBytes":null,"eventLogRetentionDays":null}}""",
                request.body.readUtf8())
        } finally { server.shutdown() }
    }

    @Test
    fun rejectsOutOfRangeSettingsBeforeSending() = runBlocking {
        val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:1")), "token")
        assertFailsWith<IllegalArgumentException> { client.updateThreadSettings(ThreadSettings(maxConcurrentPerBot = 11)) }
        assertFailsWith<IllegalArgumentException> {
            client.updateThreadSettings(ThreadSettings(eventLogMaxBytes = 1))
        }
    }
}
