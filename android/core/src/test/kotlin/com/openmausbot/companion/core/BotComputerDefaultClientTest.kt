package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BotComputerDefaultClientTest {
    @Test fun botDefaultUsesTheAdminEndpointAndRequiresAnExplicitAcknowledgementForLocalAuto() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "device-token")
            val bot = """{"id":"bot-1","threadId":"thread-1","name":"Bot","title":"Bot","description":"","notifications":true,"color":"blue","unread":false,"modelSelection":{"instanceId":"model","model":"model"},"createdAt":1,"computer":"local"}"""
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"bot":$bot}"""))
            assertEquals("local", client.setBotComputerDefault("bot-1", "local", acknowledgeLocalAuto = true).computer)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/bots/bot-1", request.path)
            assertEquals("""{"computer":"local","acknowledgeLocalAuto":true}""", request.body.readUtf8())

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"bot":$bot}"""))
            client.setBotComputerDefault("bot-1", null)
            assertEquals("""{"computer":null}""", server.takeRequest().body.readUtf8())

            assertFailsWith<IllegalArgumentException> { client.setBotComputerDefault("bot-1", "untrusted") }
            assertEquals(0, server.requestCount - 2)
        } finally { server.shutdown() }
    }
}
