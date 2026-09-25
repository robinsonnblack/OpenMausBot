package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalVmManagementClientTest {
    @Test
    fun readsStatusAndInventoryAndSavesPolicy() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"runtime":"docker","daemonUp":true,"image":true,"container":"running","ready":true,"mode":"shared","max_instances":2}"""))
            assertTrue(client.localVmStatus().ready)
            assertEquals("/api/local-computer", server.takeRequest().path)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"available":true,"instances":[{"botId":"bot-1","name":"Helper","container":"running","ready":true,"managed":true,"inUse":false}]}"""))
            assertEquals("Helper", client.localVmInventory().instances.single().name)
            assertEquals("/api/local-computer/instances", server.takeRequest().path)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"localVm":{"mode":"per-bot","maxInstances":3}}"""))
            assertEquals(LocalVmConfig("per-bot", 3), client.updateLocalVmConfig(LocalVmConfig("per-bot", 3)).localVm)
            val save = server.takeRequest()
            assertEquals("PATCH", save.method)
            assertEquals("""{"localVm":{"mode":"per-bot","maxInstances":3}}""", save.body.readUtf8())
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"runtime":"docker","daemonUp":true,"image":true,"container":"stopped","ready":false,"mode":"shared","max_instances":2}"""))
            assertEquals("stopped", client.localVmAction("stop").container)
            val action = server.takeRequest()
            assertEquals("POST", action.method)
            assertEquals("/api/local-computer/stop", action.path)
            assertEquals("application/json; charset=utf-8", action.getHeader("Content-Type"))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"runtime":"docker","daemonUp":true,"image":true,"container":"missing","ready":false,"mode":"per-bot","max_instances":2}"""))
            assertEquals("missing", client.botLocalVmAction("bot-1", "remove").container)
            val botAction = server.takeRequest()
            assertEquals("POST", botAction.method)
            assertEquals("/api/bots/bot-1/local-computer/remove", botAction.path)
            assertEquals("Bearer token", botAction.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test
    fun requiresAValidExplicitLifecycleAction() = runBlocking {
        val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:1")), "token")
        assertFailsWith<IllegalArgumentException> { client.localVmAction("recreate") }
        assertFailsWith<IllegalArgumentException> { client.botLocalVmAction("../another", "remove") }
        assertFailsWith<IllegalArgumentException> { client.botLocalVmAction("bot-1", "pull") }
        assertFailsWith<IllegalArgumentException> { client.updateLocalVmConfig(LocalVmConfig("per-bot", 5)) }
    }
}
