package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class ChiefManagedTeamsClientTest {
    @Test
    fun confirmedGrantNamesOnlySelectedTeams() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"bot":{"id":"chief","threadId":"t1","name":"A","title":"","description":"","notifications":true,"color":"green","unread":false,"modelSelection":{"instanceId":"i","model":"m"},"createdAt":1,"chiefOfStaff":true,"managedSections":["Research"]}}"""))
            assertEquals(listOf("Research"), client.setChiefManagedTeams("chief", listOf("Research", "Research"), true).managedSections)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/bots/chief", request.path)
            assertEquals("""{"managedSections":["Research"],"acknowledgePeerScope":true}""", request.body.readUtf8())
        } finally { server.shutdown() }
    }

    @Test
    fun narrowingDoesNotClaimHumanConfirmation() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"bot":{"id":"chief","threadId":"t1","name":"A","title":"","description":"","notifications":true,"color":"green","unread":false,"modelSelection":{"instanceId":"i","model":"m"},"createdAt":1,"chiefOfStaff":true,"managedSections":[]}}"""))
            client.setChiefManagedTeams("chief", emptyList(), false)
            assertEquals("""{"managedSections":[]}""", server.takeRequest().body.readUtf8())
        } finally { server.shutdown() }
    }
}
