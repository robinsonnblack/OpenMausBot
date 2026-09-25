package com.openmausbot.companion.core

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WorkspaceBackupExportClientTest {
    @Test
    fun createsAndStreamsEncryptedArchiveToCallerOutput() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse("http://127.0.0.1:${server.port}")), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"busy":false,"pendingRestore":false}"""))
            assertEquals(false, client.workspaceBackupStatus().busy)
            assertEquals("/api/workspace-backup/status", server.takeRequest().path)

            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"id":"backup-id","filename":"OpenMausBot.ombbackup","bytes":7}"""))
            val job = client.createWorkspaceBackup("long-secret-password")
            assertEquals("backup-id", job.id)
            val create = server.takeRequest()
            assertEquals("POST", create.method)
            assertEquals("/api/workspace-backup/export", create.path)
            assertTrue(create.body.readUtf8().contains("\"password\":\"long-secret-password\""))

            server.enqueue(MockResponse().setHeader("Content-Type", "application/octet-stream").setBody("archive"))
            val output = ByteArrayOutputStream()
            assertEquals(7, client.downloadWorkspaceBackup(job.id, output))
            assertEquals("archive", output.toString(Charsets.UTF_8))
            val download = server.takeRequest()
            assertEquals("/api/workspace-backup/download/backup-id", download.path)
            assertEquals("Bearer token", download.getHeader("Authorization"))
        } finally { server.shutdown() }
    }

    @Test
    fun refusesPasswordOnUnprotectedLanRoute() = runBlocking {
        val client = CompanionClient(requireNotNull(Connection.parse("192.168.1.15:8810")), "token")
        assertFailsWith<APIError.Transport> { client.createWorkspaceBackup("long-secret-password") }
        assertFailsWith<APIError.Transport> { client.downloadWorkspaceBackup("backup-id", ByteArrayOutputStream()) }
    }
}
