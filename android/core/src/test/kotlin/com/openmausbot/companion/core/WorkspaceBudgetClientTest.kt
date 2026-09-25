package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkspaceBudgetClientTest {
    @Test
    fun savesTheHostBudgetAndReadsTheConfirmedValues() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")), "token")
            server.enqueue(MockResponse().setBody("""{"edition":{"features":["budgets"]},"budgets":{"monthlyUsd":25,"warnAtPercent":75}}"""))
            val saved = client.updateWorkspaceBudget(WorkspaceBudgetConfig(25.0, 75))
            assertEquals(WorkspaceBudgetConfig(25.0, 75), saved.budgets)
            assertEquals(listOf("budgets"), saved.edition?.features)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/config", request.path)
            assertEquals("{" + "\"budgets\":{\"monthlyUsd\":25.0,\"warnAtPercent\":75}}", request.body.readUtf8())
        } finally { server.shutdown() }
    }

    @Test
    fun rejectsInvalidLimitsBeforeNetwork() = runBlocking {
        val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:1")), "token")
        assertFailsWith<IllegalArgumentException> { client.updateWorkspaceBudget(WorkspaceBudgetConfig(Double.NaN, 80)) }
        assertFailsWith<IllegalArgumentException> { client.updateWorkspaceBudget(WorkspaceBudgetConfig(5.0, 101)) }
    }
}
