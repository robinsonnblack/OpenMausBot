package com.openmausbot.companion.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PromptCaptureUsage(
    val input: Int? = null,
    val cached: Int? = null,
    val uncached: Int? = null,
    val output: Int? = null,
)

@Serializable
data class PromptCapture(
    val id: String,
    val threadId: String,
    val provider: String,
    val kind: String,
    val sentAt: String,
    val status: String,
    val body: JsonElement,
    val endpoint: String? = null,
    val httpStatus: Int? = null,
    val responseHeaders: Map<String, String>? = null,
    val usage: PromptCaptureUsage? = null,
    val durationMs: Double? = null,
    val error: String? = null,
    val omitted: Boolean = false,
)

@Serializable
data class PromptCaptureResponse(val records: List<PromptCapture>)
