package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BotSafeApprovalClientTest {
    @Test fun sendsOnlyAskOrAutoAndRequiresExplicitLocalAcknowledgement() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "device-token")
            val bot = """{"id":"bot-1","threadId":"thread-1","name":"Bot","title":"Bot","description":"","notifications":true,"color":"blue","unread":false,"modelSelection":{"instanceId":"model","model":"model"},"createdAt":1,"approvalMode":"auto"}"""
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"bot":$bot}"""))
            assertEquals("auto", client.setBotApprovalMode("bot-1", "auto", acknowledgeLocalAuto = true).approvalMode)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/bots/bot-1", request.path)
            assertEquals("""{"approvalMode":"auto","acknowledgeLocalAuto":true}""", request.body.readUtf8())

            assertFailsWith<IllegalArgumentException> { client.setBotApprovalMode("bot-1", "full") }
            assertFailsWith<IllegalArgumentException> { client.setBotApprovalMode("bot-1", "custom") }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}
