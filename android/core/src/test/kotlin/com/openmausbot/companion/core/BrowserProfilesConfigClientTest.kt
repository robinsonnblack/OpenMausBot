package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class BrowserProfilesConfigClientTest {
    @Test fun updatesNamedProfilesWithAnExpectedBaselineAndNoPartitionIdentifier() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"browserProfiles":[{"id":"work","name":"New work","partitionId":"private"}]}"""))
            val saved = client.updateBrowserProfiles(
                listOf(BrowserProfile("work", "Work")), listOf(BrowserProfile("work", "New work")),
            )
            assertEquals("New work", saved.browserProfiles.single().name)
            val request = server.takeRequest()
            assertEquals("PATCH /api/config", "${request.method} ${request.path}")
            val body = CompanionJson.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("Work", body.getValue("expectedBrowserProfiles").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
            assertEquals("New work", body.getValue("browserProfiles").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
            assertEquals(setOf("id", "name"), body.getValue("browserProfiles").jsonArray.single().jsonObject.keys)
        } finally { server.shutdown() }
    }
}
