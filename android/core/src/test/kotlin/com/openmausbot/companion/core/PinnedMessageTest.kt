package com.openmausbot.companion.core

import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PinnedMessageTest {
    @Test
    fun botPinBelongsToTheSelectedThread() {
        val bot = CompanionJson.decodeFromString<Bot>(
            """{"id":"b","threadId":"one","name":"Bot","title":"","description":"","notifications":true,"color":"blue","unread":false,"modelSelection":{"instanceId":"i","model":"m"},"createdAt":1,"pinnedMessageId":"m1","tasks":[{"threadId":"one","title":"One","createdAt":1,"pinnedMessageId":"m1"},{"threadId":"two","title":"Two","createdAt":2,"pinnedMessageId":"m2"},{"threadId":"three","title":"Three","createdAt":3}]}""",
        )
        assertEquals("m1", Chat.BotChat(bot).pinnedMessageId)
        assertEquals("m2", Chat.BotChat(bot.forTask("two")!!).pinnedMessageId)
        assertNull(Chat.BotChat(bot.forTask("three")!!).pinnedMessageId)
    }

    @Test
    fun oldHostsWithoutPinStillDecode() {
        val room = CompanionJson.decodeFromString<Room>(
            """{"id":"r","threadId":"t","name":"Room","memberIds":[],"defaultResponder":{"kind":"everyone"},"bulletin":"","unread":false,"createdAt":1}""",
        )
        assertNull(Chat.RoomChat(room).pinnedMessageId)
    }
}
