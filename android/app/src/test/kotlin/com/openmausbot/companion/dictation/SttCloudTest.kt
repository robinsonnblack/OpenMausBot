package com.openmausbot.companion.dictation

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import kotlin.test.*

class SttCloudTest {
    @Test fun everyCloudProviderSendsRealAudioAndParsesItsResponse() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            val seen = mutableListOf<okhttp3.Request>()
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                seen.add(chain.request())
                chain.proceed(chain.request().newBuilder().url(server.url("/transcribe")).build())
            }.build()
            val client = SttCloud(http)
            for(id in listOf("openrouter","openai","groq","deepgram","azure","compatible")) {
                val json=when(id) { "deepgram" -> """{"results":{"channels":[{"alternatives":[{"transcript":"Hallo Welt"}]}]}}"""; "azure" -> """{"RecognitionStatus":"Success","DisplayText":"Hallo Welt"}"""; else -> """{"text":"Hallo Welt"}""" }
                server.enqueue(MockResponse().setBody(json))
                val endpoint=if(id=="azure") "https://test.cognitiveservices.azure.com" else "https://test.example/v1"
                val cfg=SttConfig(id,"de-DE",mapOf(id to SttProfile("synthetic-key",endpoint=endpoint)))
                assertEquals("Hallo Welt",client.transcribe(cfg,ByteArray(320)))
                val request=seen.last(); val body=Buffer();request.body!!.writeTo(body)
                assertTrue(body.readUtf8().contains("RIFF"))
                assertEquals(if(id=="azure") "synthetic-key" else if(id=="deepgram") "Token synthetic-key" else "Bearer synthetic-key",request.header(if(id=="azure") "Ocp-Apim-Subscription-Key" else "Authorization"))
                if(id !in listOf("deepgram","azure")) assertTrue(request.url.encodedPath.endsWith("/audio/transcriptions"))
                server.takeRequest()
            }
        } finally { server.shutdown() }
    }
    @Test fun networkFailureAndNoSpeechAreConcreteErrors() = runBlocking {
        val server=MockWebServer();server.start()
        try {
            val client=SttCloud(OkHttpClient.Builder().addInterceptor { c->c.proceed(c.request().newBuilder().url(server.url("/")).build()) }.build())
            val cfg=SttConfig("openai",profiles=mapOf("openai" to SttProfile("synthetic")))
            server.enqueue(MockResponse().setResponseCode(401)); assertTrue(assertFailsWith<IllegalStateException> { client.transcribe(cfg,ByteArray(320)) }.message!!.contains("API key"))
            server.enqueue(MockResponse().setBody("""{"text":""}""")); assertTrue(assertFailsWith<IllegalArgumentException> { client.transcribe(cfg,ByteArray(320)) }.message!!.contains("No speech"))
        } finally { server.shutdown() }
    }
    @Test fun importedWindowsDefaultSelectsAnAvailableMobileProviderAndKeepsOtherKeys() {
        val merged=mergeSttImport(SttConfig("android",profiles=mapOf("groq" to SttProfile("existing"))),SttConfig("windows-typing","de-DE",mapOf("openrouter" to SttProfile("imported"),"unrelated" to SttProfile("excluded"))))
        assertEquals("openrouter",merged.provider); assertEquals("existing",merged.profiles["groq"]!!.key); assertFalse("unrelated" in merged.profiles)
    }
    @Test fun unsafeServiceUrlsAreRejectedBeforeAnyNetworkRequest() {
        for(url in listOf("http://external.example/v1","https://user:secret@external.example/v1","https://external.example/v1?key=secret"))
            assertFails { SttCloud().request(SttConfig("compatible",profiles=mapOf("compatible" to SttProfile(endpoint=url))),ByteArray(320)) }
    }
}
