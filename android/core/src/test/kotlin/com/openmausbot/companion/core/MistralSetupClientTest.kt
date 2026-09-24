package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MistralSetupClientTest {
    @Test
    fun storesAndChecksKeyAgainstPairedComputer() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse("http://127.0.0.1:${server.port}"))
            val client = CompanionClient(connection, "paired-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"mistral":{"configured":true}}"""))
            assertTrue(client.setMistralKey("secret-key").mistral?.configured == true)
            val save = server.takeRequest()
            assertEquals("PUT", save.method)
            assertEquals("/api/config", save.path)
            assertEquals("paired-token", save.getHeader("Authorization")?.removePrefix("Bearer "))
            assertTrue(save.body.readUtf8().contains("\"mistral\":{\"key\":\"secret-key\"}"))

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"ok":true,"check":"models","models":["mistral-large-latest"]}"""))
            val verdict = client.testMistralKey()
            assertTrue(verdict.ok)
            assertEquals(listOf("mistral-large-latest"), verdict.models)
            val check = server.takeRequest()
            assertEquals("/api/keys/test", check.path)
            val body = check.body.readUtf8()
            assertTrue(body.contains("\"provider\":\"mistral\""))
            assertFalse(body.contains("secret-key"))
        } finally { server.shutdown() }
    }

    @Test
    fun rejectsApiKeyOnOrdinaryLanRoute() = runBlocking {
        val connection = requireNotNull(Connection.parse("192.168.1.15:8810"))
        val client = CompanionClient(connection, "paired-token")
        assertFailsWith<APIError.Transport> { client.setMistralKey("secret-key") }
        assertFailsWith<APIError.Transport> { client.testMistralKey("secret-key") }
    }
}
