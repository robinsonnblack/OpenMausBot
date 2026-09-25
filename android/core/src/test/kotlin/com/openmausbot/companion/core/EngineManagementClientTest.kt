package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EngineManagementClientTest {
    @Test fun refreshesTheChosenHostEngine() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"instances":[]}"""))
            assertEquals(0, client.manageEngine("codex", "refresh-models").size)
            assertEquals("POST /api/instances/codex/refresh-models", server.takeRequest().let { "${it.method} ${it.path}" })
            assertFailsWith<IllegalArgumentException> { client.manageEngine("../config", "install") }
            assertFailsWith<IllegalArgumentException> { client.manageEngine("codex", "auth/sign-out") }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}
