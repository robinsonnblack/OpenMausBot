package com.openmausbot.companion.core

import kotlinx.coroutines.*
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.*
import java.util.concurrent.TimeUnit
import kotlin.test.*

class SttImportClientTest {
    @Test fun importsOnlyAfterServerIdentityCheckAndUsesThePairedBearer() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val connection = requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")).copy(serverEnvironmentId = "test-env")
            val client = CompanionClient(connection, "synthetic-paired-token")
            server.enqueue(MockResponse().setBody("""{"environmentId":"test-env","label":"Test"}"""))
            server.enqueue(MockResponse().setBody("""{"requestId":"r1","wrappedKey":"encrypted"}"""))
            val result = client.importSttSettings("synthetic-public-key")
            assertEquals("r1", result["requestId"]?.jsonPrimitive?.content)
            val identity = server.takeRequest(); assertNull(identity.getHeader("Authorization"))
            val request = server.takeRequest(); assertEquals("/api/transcription/import", request.path)
            assertEquals("POST", request.method); assertEquals("Bearer synthetic-paired-token", request.getHeader("Authorization"))
            assertEquals("""{"publicKey":"synthetic-public-key"}""", request.body.readUtf8())
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"Import declined on the desktop."}"""))
            val error = assertFailsWith<APIError.Status> { client.importSttSettings("synthetic-public-key") }
            assertEquals(409, error.code); assertEquals("Import declined on the desktop.", error.message)
        }
    }
    @Test fun cancellingTheImportCancelsTheWaitingHttpCall() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")), "synthetic")
            val job = launch(Dispatchers.IO) { client.importSttSettings("synthetic-public-key") }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
            withTimeout(2000) { job.cancelAndJoin() }
            assertTrue(job.isCancelled)
        }
    }
}
