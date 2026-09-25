package com.openmausbot.companion.core

import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceBackupRestoreClientTest {
    @Test
    fun uploadsPreviewsAndCommitsOnlyOnExplicitRestoreCall() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")), "token")
            server.enqueue(MockResponse().setBody("""{"id":"upload-id"}"""))
            val upload = client.uploadWorkspaceBackup(7) { ByteArrayInputStream("archive".toByteArray()) }
            assertEquals("upload-id", upload.id)
            val uploadRequest = server.takeRequest()
            assertEquals("POST", uploadRequest.method)
            assertEquals("/api/workspace-backup/upload", uploadRequest.path)
            assertEquals("7", uploadRequest.getHeader("Content-Length"))
            assertEquals("archive", uploadRequest.body.readUtf8())
            assertEquals("Bearer token", uploadRequest.getHeader("Authorization"))
            assertEquals(0, server.requestCount - 1)

            server.enqueue(MockResponse().setBody("""{"id":"stage-id","summary":{"bots":2,"warnings":["fixture"]}}"""))
            val preview = client.previewWorkspaceBackup(upload.id, "long-secret-password")
            assertEquals(2, preview.summary.bots)
            assertEquals(listOf("fixture"), preview.summary.warnings)
            val previewRequest = server.takeRequest()
            assertEquals("/api/workspace-backup/preview", previewRequest.path)
            assertTrue(previewRequest.body.readUtf8().contains("\"password\":\"long-secret-password\""))
            assertEquals(2, server.requestCount)

            server.enqueue(MockResponse().setBody("""{"id":"stage-id","restartRequired":true,"restoreId":"stage-id"}"""))
            val result = client.restoreWorkspaceBackup(preview.id)
            assertEquals(preview.id, result.id)
            assertTrue(result.restartRequired)
            val restoreRequest = server.takeRequest()
            assertEquals("/api/workspace-backup/restore", restoreRequest.path)
            assertTrue(restoreRequest.body.readUtf8().contains("\"confirmation\":\"REPLACE\""))
        } finally { server.shutdown() }
    }

    @Test
    fun refusesBackupImportOnUnprotectedLanRoute() = runBlocking {
        val client = CompanionClient(requireNotNull(Connection.parse("192.168.1.15:8810")), "token")
        assertFailsWith<APIError.Transport> { client.uploadWorkspaceBackup(7) { ByteArrayInputStream(byteArrayOf(1)) } }
        assertFailsWith<APIError.Transport> { client.previewWorkspaceBackup("id", "long-secret-password") }
        assertFailsWith<APIError.Transport> { client.restoreWorkspaceBackup("id") }
    }

    @Test
    fun rejectsUnboundedUploadBeforeConnecting() = runBlocking {
        val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:8810")), "token")
        assertFailsWith<IllegalArgumentException> { client.uploadWorkspaceBackup(0) { ByteArrayInputStream(byteArrayOf()) } }
    }
}
