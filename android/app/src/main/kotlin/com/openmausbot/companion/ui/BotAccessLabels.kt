package com.openmausbot.companion.ui

/** Read-only labels; the paired-safe profile endpoint cannot edit these permissions. */
internal fun computerAccessLabel(computer: String?): String = when (computer) {
    "browser" -> "Browser"
    "local" -> "This computer"
    "vm" -> "Local virtual machine"
    "cloud" -> "Cloud computer"
    "off" -> "Off"
    else -> "Not reported by this computer"
}

internal fun approvalAccessLabel(mode: String?, autoApprove: Boolean?): String = when (mode) {
    "ask" -> "Ask"
    "auto" -> "Auto"
    "full" -> "Full access"
    "custom" -> "Custom"
    else -> when (autoApprove) {
        true -> "Auto"
        false -> "Ask"
        null -> "Not reported by this computer"
    }
}
