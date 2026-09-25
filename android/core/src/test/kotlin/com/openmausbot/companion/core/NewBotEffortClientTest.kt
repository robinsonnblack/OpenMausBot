package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NewBotEffortClientTest {
    @Test
    fun setsAndClearsOnlyTheNewBotEffort() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"newBots":{"effort":"high"}}"""))
            assertEquals("high", client.updateNewBotEffort("high").newBots?.effort)
            val set = server.takeRequest()
            assertEquals("PATCH", set.method)
            assertEquals("/api/config", set.path)
            assertEquals("""{"newBots":{"effort":"high"}}""", set.body.readUtf8())

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"newBots":{}}"""))
            assertEquals(null, client.updateNewBotEffort(null).newBots?.effort)
            assertEquals("""{"newBots":{"effort":null}}""", server.takeRequest().body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsUnknownLevel() = runBlocking {
        val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:1")), "token")
        assertFailsWith<IllegalArgumentException> { client.updateNewBotEffort("ultra") }
    }
}
