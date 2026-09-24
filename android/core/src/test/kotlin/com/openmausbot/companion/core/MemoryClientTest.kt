package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryClientTest {
    @Test
    fun readAndSaveUseTheChosenPathAndExpectedHash() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "paired-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"path":"memory/plan.md","text":"Before","hash":"hash-1","exists":true}"""))
            val opened = client.memoryDoc("bot-1", "memory/plan.md")
            assertEquals("Before", opened.text)
            assertEquals("/api/bots/bot-1/memory/file?path=memory%2Fplan.md", server.takeRequest().path)

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"ok":true,"path":"memory/plan.md","text":"After","hash":"hash-2","exists":true,"overview":{"botId":"bot-1","workspacePath":"/tmp/bot","index":{"lines":0,"bytes":0,"maxLines":100,"maxBytes":1000,"loadedLines":0,"loadedBytes":0,"truncated":false,"hash":"index-hash"},"topics":[],"logs":[]}}"""))
            val saved = client.saveMemoryDoc("bot-1", "memory/plan.md", "After", opened.hash)
            assertEquals("hash-2", saved.hash)
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/api/bots/bot-1/memory/file", request.path)
            val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("memory/plan.md", body.getValue("path").jsonPrimitive.content)
            assertEquals("After", body.getValue("text").jsonPrimitive.content)
            assertEquals("hash-1", body.getValue("expectedHash").jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }
}
