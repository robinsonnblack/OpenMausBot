package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BotMcpSelectionClientTest {
    @Test
    fun readsSafeInventoryAndSendsExplicitOrDefaultBotSelection() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = Connection(name = "Mock host", host = "127.0.0.1", port = server.port)
            val client = CompanionClient(connection, "paired-token")
            server.enqueue(json("""{"servers":[{"name":"notes","enabled":true},{"name":"locked","enabled":false,"managedBy":"Example Org"}]}"""))
            val bot = """{"id":"b1","threadId":"t1","name":"Scout","title":"","description":"","notifications":true,"color":"green","unread":false,"modelSelection":{"instanceId":"i","model":"m"},"createdAt":1,"mcpServers":["notes"]}"""
            server.enqueue(json("""{"bot":$bot}"""))
            server.enqueue(json("""{"bot":$bot}"""))

            val inventory = client.mcpServers()
            assertEquals(listOf("notes", "locked"), inventory.map(McpServerSummary::name))
            assertEquals("Example Org", inventory[1].managedBy)
            assertEquals(listOf("notes"), client.setBotMcpServers("b1", listOf("notes")).mcpServers)
            client.setBotMcpServers("b1", null)

            assertEquals("/api/mcp/servers", server.takeRequest().path)
            val explicit = server.takeRequest()
            assertEquals("/api/bots/b1", explicit.path)
            assertEquals("PATCH", explicit.method)
            assertTrue(explicit.body.readUtf8().contains("\"mcpServers\":[\"notes\"]"))
            assertTrue(server.takeRequest().body.readUtf8().contains("\"mcpServers\":null"))
        } finally {
            server.shutdown()
        }
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun plainLanRouteRefusesInventoryBeforeSendingItsPotentiallySensitiveDetails() = runBlocking {
        val connection = Connection(id = "lan", name = "PC", host = "192.168.1.2", port = 8810)
        val error = assertFailsWith<APIError.Transport> {
            CompanionClient(connection, "paired-token").mcpServers()
        }
        assertTrue(error.message.orEmpty().contains("HTTPS or a Tailscale connection"))
    }
}
