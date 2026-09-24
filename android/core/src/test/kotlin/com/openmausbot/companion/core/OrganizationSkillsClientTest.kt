package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class OrganizationSkillsClientTest {
    @Test
    fun listsAndAddsOfferedSkillForTheChosenBot() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "paired-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"organization":{"id":"org-1","name":"Team"},"skills":[{"installId":"0123456789abcdef0123456789abcdef","packageName":"Research","publisher":"Team","release":"1.0","name":"research","description":"Research sources","added":false}]}
            """))
            val catalog = client.offeredOrganizationSkills("bot-1")
            assertEquals("Team", catalog.organization?.name)
            assertEquals("/api/org-library/skills?botId=bot-1", server.takeRequest().path)

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"skill":{"name":"research"}}"""))
            client.addOrganizationSkill("bot-1", catalog.skills.single().installId, "research")
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("bot-1", body.getValue("botId").jsonPrimitive.content)
            assertEquals("research", body.getValue("name").jsonPrimitive.content)
        } finally { server.shutdown() }
    }
}
