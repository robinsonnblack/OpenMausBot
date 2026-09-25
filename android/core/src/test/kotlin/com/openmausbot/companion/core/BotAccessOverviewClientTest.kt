package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class BotAccessOverviewClientTest {
    @Test
    fun readsOnlyTheSelectedBotsWebhookStatus() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "paired-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
                """{"webhooks":[{"id":"one","botId":"bot-1","name":"Calendar","enabled":true,"deliveryCount":3,"prompt":"private trigger"},{"id":"two","botId":"bot-2","name":"Other","enabled":false,"deliveryCount":8}],"attempts":[],"ingress":{}}"""
            ))

            val hooks = client.botWebhooks("bot-1")
            assertEquals(listOf(BotWebhook("one", "bot-1", "Calendar", true, 3)), hooks)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/webhooks", request.path)
            assertEquals("Bearer paired-token", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}
