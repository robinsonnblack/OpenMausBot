package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SharedUserProfileClientTest {
    @Test fun editsTheCompleteSharedProfile() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "device-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"profile":{"name":"Ada","email":"ada@example.com","aboutMe":"Short replies"}}"""))
            val result = client.updateSharedProfile("Ada", "ada@example.com", "Short replies")
            assertEquals("Ada", result.profile?.name)
            assertEquals("""{"profile":{"name":"Ada","email":"ada@example.com","aboutMe":"Short replies"}}""",
                server.takeRequest().body.readUtf8())
        } finally { server.shutdown() }
    }
    @Test fun readsAndWritesOnlyTheSharedProfileField() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(
                requireNotNull(Connection.parse(server.url("/").toString())),
                "device-token",
            )
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"profile":{"name":"Ada","email":"","aboutMe":"I prefer concise replies."}}"""))
            assertEquals("I prefer concise replies.", client.config().profile?.aboutMe)
            assertEquals("GET", server.takeRequest().method)

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"profile":{"name":"Ada","email":"","aboutMe":"My new profile"}}"""))
            assertEquals("My new profile", client.updateAboutMe("My new profile").profile?.aboutMe)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/config", request.path)
            assertEquals("""{"profile":{"aboutMe":"My new profile"}}""", request.body.readUtf8())
            assertEquals("Bearer device-token", request.getHeader("Authorization"))

            assertFailsWith<IllegalArgumentException> { client.updateAboutMe("a".repeat(24_001)) }
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
