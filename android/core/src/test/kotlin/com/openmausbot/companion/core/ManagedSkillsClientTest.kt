package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ManagedSkillsClientTest {
    @Test
    fun listReviewAndEnableUseThePairedBotRoutes() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "paired-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"skills":[{"name":"research","description":"Research sources","enabled":false,"source":"owner/repo","warnings":[]}],"staged":[]}"""))
            val skills = client.managedSkills("bot-1")
            assertFalse(skills.skills.single().enabled)
            assertEquals("/api/bots/bot-1/skills", server.takeRequest().path)

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"text":"# Research\\nRead sources first."}"""))
            client.managedSkillText("bot-1", "research")
            assertEquals("/api/bots/bot-1/skills/research", server.takeRequest().path)

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"skill":{"name":"research","enabled":true}}"""))
            client.setManagedSkillEnabled("bot-1", "research", true)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/bots/bot-1/skills/research", request.path)
            assertEquals("true", CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject.getValue("enabled").jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }
}
