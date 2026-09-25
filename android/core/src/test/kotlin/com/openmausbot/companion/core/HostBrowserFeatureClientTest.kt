package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class HostBrowserFeatureClientTest {
    @Test
    fun patchesOnlyHostBrowserFeature() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"features":{"browser":true},"browserEngine":{"kind":"engine"}}"""))
            assertEquals(true, client.updateHostBrowserEnabled(true).features?.browser)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/config", request.path)
            assertEquals("""{"features":{"browser":true}}""", request.body.readUtf8())
        } finally { server.shutdown() }
    }
}
