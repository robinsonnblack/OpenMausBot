package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoomTurnTimeoutClientTest {
    @Test
    fun updatesOnlyTheRoomTimeout() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"rooms":{"turnTimeoutMinutes":25}}"""))
            assertEquals(25, client.updateRoomTurnTimeout(25).rooms?.turnTimeoutMinutes)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/config", request.path)
            assertEquals("""{"rooms":{"turnTimeoutMinutes":25}}""", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun rejectsInvalidMinutesBeforeRequest() = runBlocking {
        val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:1")), "token")
        assertFailsWith<IllegalArgumentException> { client.updateRoomTurnTimeout(0) }
        assertFailsWith<IllegalArgumentException> { client.updateRoomTurnTimeout(1441) }
    }
}
