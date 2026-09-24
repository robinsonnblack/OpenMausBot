package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageDeletionClientTest {
    @Test fun selectionAndDeletionUseTheOwnerEndpoints() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "device-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"ids":["m1"],"allIds":["m1","m2"]}"""))
            assertEquals(listOf("m1", "m2"), client.messageDeletionSelection("thread-1").allIds)
            assertEquals("GET", server.takeRequest().method)

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"ids":["m1"],"activeLeafId":"m2"}"""))
            assertEquals(listOf("m1"), client.deleteMessages("thread-1", listOf("m1")).ids)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/threads/thread-1/messages/delete", request.path)
            assertEquals("""{"ids":["m1"]}""", request.body.readUtf8())
        } finally { server.shutdown() }
    }

    @Test fun deletionFrameRemovesRowsAndResetsTheActiveLeaf() {
        val frame = CompanionJson.decodeFromString<Frame>(
            """{"kind":"messages.deleted","threadId":"thread-1","ids":["m1"],"activeLeafId":"m2"}""",
        )
        val messages = listOf(
            Message("m1", Message.Role.USER, Message.Kind.TEXT, 1.0, text = "old"),
            Message("m2", Message.Role.BOT, Message.Kind.TEXT, 2.0, text = "kept"),
        )
        val result = CompanionState(messages = mapOf("thread-1" to messages)).apply(frame)
        assertEquals(listOf("m2"), result.transcript("thread-1").map(Message::id))
        assertEquals("m2", result.activeLeafIds["thread-1"])
    }
}
