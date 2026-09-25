package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommandAllowlistClientTest {
    @Test
    fun readsAddsAndRemovesExactRuleForOneBot() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(Connection(name = "Mock host", host = "127.0.0.1", port = server.port), "paired-token")
            val status = """{"rules":[{"id":"r1","command":"echo hello","cwd":"C:\\\\work","providerInstanceId":"codex"}],"context":{"providerInstanceId":"codex","cwd":"C:\\\\work"},"supported":true}"""
            repeat(3) { server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(status)) }

            assertEquals("r1", client.botCommandAllowlist("b1").rules.single().id)
            assertTrue(client.addBotCommandRule("b1", "echo hello", "C:\\work", "codex").supported)
            assertEquals(1, client.removeBotCommandRule("b1", "r1").rules.size)

            assertEquals("GET /api/bots/b1/command-allowlist", server.takeRequest().let { "${it.method} ${it.path}" })
            val add = server.takeRequest()
            assertEquals("POST /api/bots/b1/command-allowlist", "${add.method} ${add.path}")
            val body = add.body.readUtf8()
            assertTrue(body.contains("\"command\":\"echo hello\""))
            assertTrue(body.contains("\"providerInstanceId\":\"codex\""))
            assertEquals("DELETE /api/bots/b1/command-allowlist/r1", server.takeRequest().let { "${it.method} ${it.path}" })
        } finally { server.shutdown() }
    }

    @Test
    fun plainLanRefusesCommandRulesBeforeHttp() = runBlocking {
        val client = CompanionClient(Connection(name = "LAN", host = "192.168.1.2", port = 8810), "paired-token")
        assertFailsWith<APIError.Transport> { client.botCommandAllowlist("b1") }
        assertFailsWith<APIError.Transport> { client.addBotCommandRule("b1", "echo hello", "C:\\work", "codex") }
        assertFailsWith<APIError.Transport> { client.removeBotCommandRule("b1", "r1") }
    }
}
