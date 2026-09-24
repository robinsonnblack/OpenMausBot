package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class BotWorkingFolderClientTest {
    @Test fun sendsDesktopPathForValidationAndCanResetToPrivateFolder() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "device-token")
            val bot = """{"id":"bot-1","threadId":"thread-1","name":"Bot","title":"Bot","description":"","notifications":true,"color":"blue","unread":false,"modelSelection":{"instanceId":"model","model":"model"},"createdAt":1,"cwd":"C:\\Work"}"""
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"bot":$bot}"""))
            assertEquals("C:\\Work", client.setBotWorkingFolder("bot-1", "C:\\Work").cwd)
            val first = server.takeRequest()
            assertEquals("PATCH", first.method)
            assertEquals("/api/bots/bot-1", first.path)
            assertEquals("""{"cwd":"C:\\Work"}""", first.body.readUtf8())

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"bot":$bot}"""))
            client.setBotWorkingFolder("bot-1", null)
            assertEquals("""{"cwd":null}""", server.takeRequest().body.readUtf8())
            assertEquals("C:\\Work", CompanionJson.decodeFromString<BotTask>(
                """{"threadId":"thread-1","title":"Task","createdAt":1,"cwd":"C:\\Work"}""",
            ).cwd)
        } finally { server.shutdown() }
    }
}
