package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceBillingClientTest {
    @Test
    fun savesPriceListIncludingOptionalCachedRate() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")), "token")
            val wanted = WorkspaceBillingConfig("USD", mapOf(
                "default" to ModelBillingPrice(4.0, 8.0),
                "driver/model" to ModelBillingPrice(1.2, 2.4, 0.3),
            ))
            server.enqueue(MockResponse().setBody("""{"edition":{"features":["billing"]},"billing":{"currency":"USD","prices":{"default":{"inputPerMillion":4,"outputPerMillion":8},"driver/model":{"inputPerMillion":1.2,"outputPerMillion":2.4,"cachedInputPerMillion":0.3}}}}"""))
            val saved = client.updateWorkspaceBilling(wanted)
            assertEquals(wanted, saved.billing)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/config", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"billing\""))
            assertTrue(body.contains("\"driver/model\""))
            assertTrue(body.contains("\"cachedInputPerMillion\":0.3"))
            assertTrue(!body.substringBefore("driver/model").contains("cachedInputPerMillion"))
        } finally { server.shutdown() }
    }

    @Test
    fun rejectsInvalidRatesAndCurrencyBeforeNetwork() = runBlocking {
        val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:1")), "token")
        assertFailsWith<IllegalArgumentException> { client.updateWorkspaceBilling(WorkspaceBillingConfig("usd")) }
        assertFailsWith<IllegalArgumentException> {
            client.updateWorkspaceBilling(WorkspaceBillingConfig(prices = mapOf("default" to ModelBillingPrice(Double.NaN, 1.0))))
        }
        assertFailsWith<IllegalArgumentException> {
            client.updateWorkspaceBilling(WorkspaceBillingConfig(prices = mapOf("../model" to ModelBillingPrice(-1.0, 1.0))))
        }
    }
}
