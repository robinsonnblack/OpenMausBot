package com.openmausbot.companion.dictation

import android.content.Context
fun sttErrorText(context: Context, message: String): String {
    if (!context.resources.configuration.locales[0].language.equals("de")) return message
    val fixed = mapOf(
        "Import declined on the desktop." to "Übernahme am Desktop abgelehnt.",
        "No STT keys are saved on this desktop." to "Auf diesem Desktop sind keine STT-Schlüssel gespeichert.",
        "Desktop STT keys could not be read." to "STT-Schlüssel am Desktop konnten nicht gelesen werden.",
        "Update the desktop app to use STT import." to "Aktualisiere die Desktop-App für die STT-Übernahme.",
        "The desktop did not confirm the import. Try again." to "Keine Bestätigung vom Desktop erhalten. Erneut versuchen.",
        "An import is already pending. Finish it on the desktop." to "Eine Übernahme wartet bereits auf die Bestätigung am Desktop.",
        "STT import requires provider access for this paired phone." to "Für die Übernahme benötigt dieses Handy das Recht zur Anbieter-Verwaltung.",
        "STT import access was revoked." to "Das Recht zur Übernahme wurde entzogen.",
        "Import expired. Try again." to "Übernahme abgelaufen. Erneut versuchen.",
        "The encrypted STT import could not be verified. Try again." to "Verschlüsselte Übernahme konnte nicht geprüft werden. Erneut versuchen.",
        "This computer is offline." to "Der gekoppelte Desktop ist nicht erreichbar.",
        "STT settings could not be read. Open STT settings and try again." to "STT-Einstellungen konnten nicht gelesen werden. Öffne die Spracheingabe-Einstellungen.",
        "STT settings could not be saved." to "STT-Einstellungen konnten nicht gespeichert werden.",
        "Add an API key or import it from the desktop." to "API-Schlüssel eingeben oder vom Desktop übernehmen.",
        "Enter the STT service URL." to "Adresse des STT-Dienstes eingeben.",
        "Use HTTPS for the STT service." to "Für den STT-Dienst eine HTTPS-Adresse verwenden.",
        "Enter your Azure Speech endpoint." to "Adresse deiner Azure-Speech-Ressource eingeben.",
        "Azure needs a language, for example de-DE." to "Azure benötigt eine Sprache, zum Beispiel de-DE.",
        "Choose a valid speech language." to "Eine gültige Sprache eingeben, zum Beispiel de-DE oder auto.",
        "The STT provider rejected the API key." to "Der STT-Anbieter hat den API-Schlüssel abgelehnt.",
        "STT limit reached or insufficient credit." to "STT-Limit erreicht oder Guthaben aufgebraucht.",
        "No speech recognized. Try again." to "Keine Sprache erkannt. Erneut versuchen.",
        "No speech was recorded." to "Es wurde keine Sprache aufgenommen.",
        "Could not reach the STT provider. Check your connection." to "STT-Anbieter nicht erreichbar. Verbindung prüfen.",
        "The STT provider returned an invalid response." to "STT-Anbieter hat eine ungültige Antwort geliefert.",
        "The STT provider returned an empty response." to "STT-Anbieter hat eine leere Antwort geliefert.",
        "Couldn't start the microphone." to "Mikrofon konnte nicht gestartet werden.",
        "Could not start the microphone." to "Mikrofon konnte nicht gestartet werden.",
        "The microphone did not become ready. Try again." to "Mikrofon wurde nicht bereit. Erneut versuchen.",
        "Transcription timed out. Try again." to "Transkription hat zu lange gedauert. Erneut versuchen.",
        "Dictation isn't available for this language." to "Android-Spracherkennung ist für diese Sprache nicht verfügbar.",
        "Couldn't transcribe that." to "Transkription fehlgeschlagen.",
        "Dictation needs Microphone access. Enable it in Settings → MausBot." to "Mikrofonzugriff fehlt. In den Android-App-Einstellungen für OpenMausBot erlauben."
    )
    fixed[message]?.let { return it }
    Regex("""Android speech recognition failed \(code ([0-9]+)\)\.""").matchEntire(message)?.let { return "Android-Spracherkennung abgebrochen (Fehlercode ${it.groupValues[1]})." }
    Regex("""STT provider returned HTTP ([0-9]+)\.""").matchEntire(message)?.let { return "STT-Anbieter meldet HTTP ${it.groupValues[1]}." }
    return message
}
