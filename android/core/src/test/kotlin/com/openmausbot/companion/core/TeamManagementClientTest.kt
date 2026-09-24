package com.openmausbot.companion.core

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamManagementClientTest {
    @Test fun teamActionsUseTheExistingAdminRoutesWithoutDeletingBots() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val client = CompanionClient(requireNotNull(Connection.parse(server.url("/").toString())), "token")
            fun reply(sections: String) = MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"sections":$sections}""")

            server.enqueue(reply("""["Team A"]"""))
            assertEquals(listOf("Team A"), client.teamSections())
            assertEquals("GET /api/sidebar-sections", server.takeRequest().let { "${it.method} ${it.path}" })

            server.enqueue(reply("""["Team B"]"""))
            assertEquals(listOf("Team B"), client.renameTeam("Team A", "Team B"))
            server.takeRequest().let {
                assertEquals("PATCH /api/sidebar-sections?section=Team%20A", "${it.method} ${it.path}")
                assertEquals("""{"name":"Team B"}""", it.body.readUtf8())
            }

            server.enqueue(reply("""["Team B"]"""))
            client.updateTeamMembers("Team B", listOf("bot-2"), listOf("bot-1"))
            server.takeRequest().let {
                assertEquals("PUT /api/sidebar-sections?section=Team%20B", "${it.method} ${it.path}")
                assertEquals("""{"addBotIds":["bot-2"],"removeBotIds":["bot-1"]}""", it.body.readUtf8())
            }

            server.enqueue(reply("[]"))
            client.deleteTeam("Team B")
            assertEquals("DELETE /api/sidebar-sections?section=Team%20B",
                server.takeRequest().let { "${it.method} ${it.path}" })
        } finally { server.shutdown() }
    }
}
