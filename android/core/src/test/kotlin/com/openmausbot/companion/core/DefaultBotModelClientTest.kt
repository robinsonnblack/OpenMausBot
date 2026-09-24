package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefaultBotModelClientTest {
    @Test
    fun savesAndReadsBackModelInheritedByFutureBots() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(
                requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")),
                "paired-token",
            )
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"modelSelection":{"instanceId":"codex","model":"gpt-6-luna","effort":"low"},
                 "suggestedName":"New bot"}
            """))
            val choice = ModelSelection("codex", "gpt-6-luna", "low")
            assertEquals(choice, client.setDefaultBotModel(choice).modelSelection)
            val save = server.takeRequest()
            assertEquals("PATCH", save.method)
            assertEquals("/api/config", save.path)
            assertTrue(save.body.readUtf8().contains("\"defaultModelSelection\""))
            assertEquals("/api/bot-defaults", server.takeRequest().path)
        } finally { server.shutdown() }
    }
}
