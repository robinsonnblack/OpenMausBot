package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class BotBrowserAccessClientTest {
    @Test
    fun changesOnlyTheChosenBotsBuiltInBrowserAccess() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"bot":{"id":"bot-1","threadId":"t1","name":"A","title":"","description":"","notifications":true,"color":"green","unread":false,"modelSelection":{"instanceId":"i","model":"m"},"createdAt":1,"browser":false}}"""))
            assertEquals(false, client.setBotBrowserAccess("bot-1", false).browser)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/bots/bot-1", request.path)
            assertEquals("""{"browser":false}""", request.body.readUtf8())
        } finally { server.shutdown() }
    }

    @Test
    fun decodesHostBrowserAvailabilityWithoutSecrets() {
        val status = CompanionJson.decodeFromString<ConfigStatus>(
            """{"browserEngine":{"kind":"engine"},"features":{"browser":true}}"""
        )
        assertEquals("engine", status.browserEngine?.kind)
        assertEquals(true, status.features?.browser)
    }
}
