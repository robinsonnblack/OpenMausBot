package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class BotCreationModelClientTest {
    @Test fun createsBotWithAnExplicitAvailableModelAndTeam() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"modelSelection":{"instanceId":"codex","model":"luna"},"suggestedName":"New bot"}"""))
            assertEquals("luna", client.botCreationOptions().modelSelection.model)
            assertEquals("GET /api/bot-defaults", server.takeRequest().let { "${it.method} ${it.path}" })

            val bot = """{"id":"bot-1","threadId":"thread-1","name":"Researcher","title":"Research",
                "description":"Find sources","notifications":true,"color":"blue","unread":false,
                "modelSelection":{"instanceId":"codex","model":"sol"},"createdAt":1}"""
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"bot":$bot}"""))
            assertEquals("sol", client.createBot("Researcher", "Research", "Find sources",
                ModelSelection("codex", "sol"), "Lab").modelSelection.model)
            server.takeRequest().let { request ->
                assertEquals("POST /api/bots", "${request.method} ${request.path}")
                val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
                assertEquals("Researcher", body.getValue("name").jsonPrimitive.content)
                assertEquals("Lab", body.getValue("section").jsonPrimitive.content)
                assertEquals("true", body.getValue("requireAvailableModel").jsonPrimitive.content)
                assertEquals("sol", body.getValue("modelSelection").jsonObject.getValue("model").jsonPrimitive.content)
            }
        } finally { server.shutdown() }
    }
}
