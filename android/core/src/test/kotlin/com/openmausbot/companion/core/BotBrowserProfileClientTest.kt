package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class BotBrowserProfileClientTest {
    @Test fun readsConfiguredProfilesAndSelectsOneForABot() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"browserProfiles":[{"id":"work","name":"Work","partitionId":"secret-routing"}]}"""))
            assertEquals("Work", client.config().browserProfiles.single().name)
            assertEquals("GET /api/config", server.takeRequest().let { "${it.method} ${it.path}" })

            val bot = """{"id":"bot-1","threadId":"thread-1","name":"Bot","title":"Bot",
                "description":"","notifications":true,"color":"blue","unread":false,
                "modelSelection":{"instanceId":"model","model":"model"},"createdAt":1,
                "browserProfile":"work"}"""
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"bot":$bot}"""))
            assertEquals("work", client.setBotBrowserProfile("bot-1", "work").browserProfile)
            assertEquals("""{"browserProfile":"work"}""", server.takeRequest().body.readUtf8())

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"bot":$bot}"""))
            client.setBotBrowserProfile("bot-1", null)
            assertEquals("""{"browserProfile":null}""", server.takeRequest().body.readUtf8())
        } finally { server.shutdown() }
    }
}
