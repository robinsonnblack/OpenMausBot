package com.openmausbot.companion.audio

import kotlin.test.*
import kotlinx.serialization.json.*
import okio.Buffer

class PhoneSpeechTest {
    @Test fun longTextIsSplitWithoutLosingWords() {
        val text = (1..400).joinToString(" ") { "Word$it." }
        val parts = speechParts(text, 100)
        assertTrue(parts.all { it.length <= 100 })
        assertEquals(text, parts.joinToString(" "))
        assertEquals(listOf("abcdefgh", "ijkl"), speechParts("abcdefghijkl", 8))
    }
    @Test fun directProvidersReceiveTheTextAndTheirOwnCredentials() {
        for (provider in listOf("elevenlabs", "fish", "xai", "chatterbox")) {
            val request = phoneVoiceRequest(PhoneVoiceConfig(provider, "synthetic-key", endpoint = "http://100.77.8.101:8000/v1"), "Hallo Welt", "my-voice")
            val buffer = Buffer(); request.body!!.writeTo(buffer)
            val body = Json.parseToJsonElement(buffer.readUtf8()).jsonObject
            assertEquals("Hallo Welt", (body["text"] ?: body["input"])!!.jsonPrimitive.content)
            assertEquals(if (provider == "chatterbox") "/v1/audio/speech" else if (provider == "elevenlabs") "/v1/text-to-speech/my-voice" else "/v1/tts", request.url.encodedPath)
            if (provider == "elevenlabs") assertEquals("synthetic-key", request.header("xi-api-key"))
            if (provider == "fish" || provider == "xai") assertEquals("Bearer synthetic-key", request.header("Authorization"))
        }
    }
    @Test fun remotePlainHttpIsRejectedButTailnetAddressesWork() {
        assertFails { phoneVoiceRequest(PhoneVoiceConfig("chatterbox", endpoint = "http://example.com"), "Hi", "v") }
        assertTrue(isTailnetHost("100.64.0.1")); assertTrue(isTailnetHost("100.127.255.255"))
        assertFalse(isTailnetHost("100.128.0.1")); assertFalse(isTailnetHost("100.64.999.1"))
    }
}
