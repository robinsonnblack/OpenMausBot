package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChiefAppointmentClientTest {
    @Test
    fun appointsBotThroughPairedServer() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(
                requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")),
                "paired-token",
            )
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"bot":{"id":"bot-1","threadId":"thread-1","name":"Mark","title":"Manager",
                "description":"","notifications":true,"color":"blue","unread":false,
                "modelSelection":{"instanceId":"codex","model":"luna"},"createdAt":1,
                "section":"Planning","chiefOfStaff":true}}
            """))
            val bot = client.setChiefOfStaff("bot-1", true)
            assertTrue(bot.chiefOfStaff == true)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/bots/bot-1", request.path)
            assertEquals("{\"chiefOfStaff\":true}", request.body.readUtf8())
        } finally { server.shutdown() }
    }
}
