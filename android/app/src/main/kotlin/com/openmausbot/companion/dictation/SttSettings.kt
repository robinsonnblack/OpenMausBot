package com.openmausbot.companion.dictation

import android.content.Context
import com.openmausbot.companion.core.CompanionJson
import com.openmausbot.companion.storage.KeystoreTokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable data class SttProfile(val key: String = "", val model: String = "", val endpoint: String = "")
@Serializable data class SttConfig(val provider: String = "android", val language: String = "auto", val profiles: Map<String, SttProfile> = emptyMap())
data class SttProvider(val id: String, val name: String, val model: String = "")
val STT_PROVIDERS = listOf(SttProvider("android", "Android"), SttProvider("openrouter", "OpenRouter", "openai/whisper-large-v3"),
    SttProvider("openai", "OpenAI", "gpt-4o-transcribe"), SttProvider("groq", "Groq", "whisper-large-v3-turbo"),
    SttProvider("deepgram", "Deepgram", "nova-3"), SttProvider("azure", "Azure Speech"), SttProvider("compatible", "OpenAI-compatible", "whisper-1"))
class SttStore(context: Context) {
    private val prefs by lazy { KeystoreTokenStore.openPrefs(context.applicationContext) }
    fun load(): SttConfig = try { prefs.getString("stt.settings", null)?.let { CompanionJson.decodeFromString<SttConfig>(it).also { cfg -> require(STT_PROVIDERS.any { p -> p.id == cfg.provider }) } } ?: SttConfig() } catch (_: Exception) { error("STT settings could not be read.") }
    fun save(value: SttConfig) {
        require(STT_PROVIDERS.any { it.id == value.provider }) { "Unknown STT provider." }
        check(prefs.edit().putString("stt.settings", CompanionJson.encodeToString(value)).commit()) { "STT settings could not be saved." }
    }
}
fun mergeSttImport(current: SttConfig, incoming: SttConfig): SttConfig {
    val supported = incoming.profiles.filterKeys { id -> STT_PROVIDERS.any { it.id == id && id != "android" } }
    require(supported.isNotEmpty()) { "No STT keys are saved on this desktop." }
    val selected = incoming.provider.takeIf { it in supported } ?: current.provider.takeIf { it != "android" }
        ?: STT_PROVIDERS.first { it.id in supported }.id
    return current.copy(provider = selected, language = incoming.language, profiles = current.profiles + supported)
}
