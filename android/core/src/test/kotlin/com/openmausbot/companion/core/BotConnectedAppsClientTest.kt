package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class BotConnectedAppsClientTest {
    @Test
    fun changesOnlyTheChosenBotsConnectedAppAccess() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"bot":{"id":"bot-1","threadId":"t1","name":"A","title":"","description":"","notifications":true,"color":"green","unread":false,"modelSelection":{"instanceId":"i","model":"m"},"createdAt":1,"composio":false}}"""))
            assertEquals(false, client.setBotConnectedApps("bot-1", false).composio)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/bots/bot-1", request.path)
            assertEquals("""{"composio":false}""", request.body.readUtf8())
        } finally { server.shutdown() }
    }
}
