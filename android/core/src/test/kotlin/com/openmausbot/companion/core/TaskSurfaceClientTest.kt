package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskSurfaceClientTest {
    @Test fun taskSurfaceParsesAndNullResetsTheOverride() = runBlocking {
        val task = CompanionJson.decodeFromString<BotTask>(
            """{"threadId":"thread-1","title":"Work","createdAt":1,"surface":"browser"}""",
        )
        assertEquals("browser", task.surface)
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "device-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            client.setTaskSurface("bot-1", "thread-1", null)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/bots/bot-1/tasks/thread-1", request.path)
            assertEquals("""{"surface":null}""", request.body.readUtf8())
            assertNull(CompanionJson.decodeFromString<BotTask>(
                """{"threadId":"thread-1","title":"Work","createdAt":1,"surface":null}""",
            ).surface)
        } finally { server.shutdown() }
    }
}
