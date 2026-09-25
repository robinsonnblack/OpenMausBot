package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillAuthoringClientTest {
    @Test
    fun patchesOnlySkillAuthoringAndReadsTheServerDefault() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(Connection(name = "Mock", host = "127.0.0.1", port = server.port), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"features":{"skillAuthoring":false,"browser":true}}"""))
            val status = client.updateSkillAuthoringEnabled(false)
            assertEquals(false, status.features?.skillAuthoring)
            assertEquals(true, status.features?.browser)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/config", request.path)
            assertEquals("{" + "\"features\":{\"skillAuthoring\":false}}", request.body.readUtf8())
            assertTrue(FeatureFlags().skillAuthoring != false)
        } finally { server.shutdown() }
    }
}
