package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceUsageClientTest {
    @Test
    fun readsUsageWithPeriodAndGrouping() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val connection = requireNotNull(Connection.parse(server.url("/").toString()))
            val client = CompanionClient(connection, "paired-token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"from":"2026-09-01T00:00:00.000Z","to":"2026-09-24T23:59:59.999Z","groupBy":"bot",
                 "groups":[],"total":{"key":"all","label":"Total","turns":2,"input":100,"cachedInput":60,"output":20,"costUsd":0.01,"unpriced":0},
                 "budget":{"month":"2026-09","monthlyUsd":10,"spentUsd":1,"percent":10,"warnAtPercent":80,"warn":false,"exceeded":false}}
            """))
            val report = client.workspaceUsage("2026-09-01", "2026-09-24", "bot")
            assertEquals(60, report.total.cachedInput)
            assertEquals(1.0, report.budget?.spentUsd)
            assertEquals("/api/usage?from=2026-09-01&to=2026-09-24&groupBy=bot", server.takeRequest().path)
        } finally { server.shutdown() }
    }
}
