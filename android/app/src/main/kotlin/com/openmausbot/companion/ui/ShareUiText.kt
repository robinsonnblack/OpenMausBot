package com.openmausbot.companion.ui

import android.content.Context
import com.openmausbot.companion.R

/** Localize the share policy's stable presentation text at the Android UI boundary. */
internal fun shareUiText(context: Context, text: String): String = when (text) {
    "Automatic" -> context.getString(R.string.android_share_route_automatic)
    "Secure HTTPS" -> context.getString(R.string.android_share_route_https)
    "Local network" -> context.getString(R.string.android_share_route_local)
    "Bot" -> context.getString(R.string.android_share_bot)
    "Channel" -> context.getString(R.string.android_share_channel)
    "Link" -> context.getString(R.string.android_share_link)
    "Text" -> context.getString(R.string.android_share_text)
    "Keep the optional instruction under 20,000 characters." -> context.getString(R.string.android_share_instruction_limit)
    "Every bot that may answer in this channel must use a model that supports images." -> context.getString(R.string.android_share_channel_images)
    "Unlock this phone, then try sharing again." -> context.getString(R.string.android_share_unlock_phone)
    "This saved connection is no longer available on this phone. Remove it and pair again." -> context.getString(R.string.android_share_connection_gone)
    "Couldn't reach your computer. Keep OpenMausBot open and Phone access on, then try again." -> context.getString(R.string.android_share_computer_offline)
    "Open the OpenMausBot app once after updating. If this phone still isn't connected, pair it before sharing." -> context.getString(R.string.android_share_not_paired)
    "There aren't any bots or channels to send this to yet. Create one on your computer first." -> context.getString(R.string.android_share_no_destinations)
    "Update OpenMausBot on this computer before sharing images." -> context.getString(R.string.android_share_update_for_images)
    "There isn't any text, link, image, or supported document to send." -> context.getString(R.string.android_share_nothing_supported)
    "Send up to 4 items at a time." -> context.getString(R.string.android_share_too_many)
    "That text is too large to share. Send a shorter selection." -> context.getString(R.string.android_share_text_too_large)
    "Sending took too long. Check your connection and try again." -> context.getString(R.string.android_share_timeout)
    "OpenMausBot couldn't send this. Please try again." -> context.getString(R.string.android_share_generic_error)
    "This phone's pairing has expired. Open OpenMausBot and pair it again." -> context.getString(R.string.android_share_pairing_expired)
    "1 unsupported item was left out." -> context.getString(R.string.android_share_ignored_one)
    else -> when {
        text.startsWith("Bot · ") -> context.getString(R.string.android_share_bot_task, text.removePrefix("Bot · "))
        text.startsWith("Channel · ") -> context.getString(R.string.android_share_channel_task, text.removePrefix("Channel · "))
        text.endsWith(" links") && text.substringBefore(' ').toIntOrNull() != null -> context.getString(R.string.android_share_links, text.substringBefore(' ').toInt())
        text.endsWith(" text items") && text.substringBefore(' ').toIntOrNull() != null -> context.getString(R.string.android_share_text_items, text.substringBefore(' ').toInt())
        text.endsWith(" unsupported items were left out.") && text.substringBefore(' ').toIntOrNull() != null -> context.getString(R.string.android_share_ignored_many, text.substringBefore(' ').toInt())
        text.startsWith("Couldn't reach ") && text.endsWith(". Keep OpenMausBot open and Phone access on, then try again.") ->
            context.getString(R.string.android_share_named_offline, text.removePrefix("Couldn't reach ").substringBefore(". Keep OpenMausBot"))
        text.endsWith("'s current model doesn't support images. Choose another bot or share without the image.") ->
            context.getString(R.string.android_share_bot_no_images, text.substringBefore("'s current model"))
        text.matches(Regex(".+ is larger than [0-9]+ MB\\.")) ->
            context.getString(R.string.android_share_item_too_large, text.substringBefore(" is larger than "), text.substringAfter(" is larger than ").substringBefore(' ').toInt())
        text.endsWith(" isn't a supported document. Try PDF, text, Word, Excel, or PowerPoint.") ->
            context.getString(R.string.android_share_unsupported_document, text.substringBefore(" isn't a supported document."))
        text.startsWith("OpenMausBot couldn't read ") && text.endsWith(". Try exporting it to Files first.") ->
            context.getString(R.string.android_share_unreadable, text.removePrefix("OpenMausBot couldn't read ").substringBefore(". Try exporting"))
        else -> text
    }
}
