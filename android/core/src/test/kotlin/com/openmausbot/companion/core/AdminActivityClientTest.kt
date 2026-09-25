package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminActivityClientTest {
    @Test
    fun readsFilteredActivityAndExportsTheSameRange() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")), "token")
            val filter = AdminActivityFilter(who = "Alex", what = "config", from = "2026-09-01", to = "2026-09-25")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"entries":[{"type":"admin","at":"2026-09-25T12:00:00.000Z","who":"Alex","what":"config","action":"config.update","changed":["threads.maxConcurrentPerBot"],"before":{"threads.maxConcurrentPerBot":3},"after":{"threads.maxConcurrentPerBot":4}}],"total":1,"retentionDays":180,"recording":true}"""))
            val page = client.adminActivity(filter)
            assertEquals(1, page.total)
            assertEquals("config.update", page.entries.single().action)
            assertEquals(listOf("threads.maxConcurrentPerBot"), page.entries.single().changed)
            val listRequest = server.takeRequest()
            assertEquals("/api/admin-activity?who=Alex&what=config&from=2026-09-01&to=2026-09-25", listRequest.path)
            assertEquals("Bearer token", listRequest.getHeader("Authorization"))

            server.enqueue(MockResponse().setHeader("Content-Type", "text/csv").setBody("time,type\n2026-09-25,admin\n"))
            assertTrue(client.adminActivityCsv(filter).toString(Charsets.UTF_8).contains("2026-09-25,admin"))
            assertEquals("/api/admin-activity.csv?who=Alex&what=config&from=2026-09-01&to=2026-09-25",
                server.takeRequest().path)
        } finally { server.shutdown() }
    }
}
