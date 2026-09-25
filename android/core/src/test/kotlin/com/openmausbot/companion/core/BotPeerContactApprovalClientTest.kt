package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class BotPeerContactApprovalClientTest {
    @Test
    fun changesOnlyTheChosenBotsPeerContactApproval() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"bot":{"id":"bot-1","threadId":"t1","name":"A","title":"","description":"","notifications":true,"color":"green","unread":false,"modelSelection":{"instanceId":"i","model":"m"},"createdAt":1,"approvePeerComms":true}}"""))
            assertEquals(true, client.setBotPeerContactApproval("bot-1", true).approvePeerComms)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/bots/bot-1", request.path)
            assertEquals("""{"approvePeerComms":true}""", request.body.readUtf8())
        } finally { server.shutdown() }
    }
}
