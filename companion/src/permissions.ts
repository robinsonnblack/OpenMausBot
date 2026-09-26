/** One authoritative catalogue for both kinds of phone pairing. */
export const PERMISSIONS = [
  ["chatRead", "Chats lesen und suchen", "Read and search chats", "Nachrichten, Chatlisten und Suchergebnisse ansehen.", "View messages, chat lists and search results."],
  ["chatSend", "Nachrichten senden", "Send messages", "Nachrichten senden und laufende Antworten unterbrechen.", "Send messages and interrupt replies."],
  ["chatEdit", "Nachrichten bearbeiten", "Edit messages", "Bestehende Nachrichten bearbeiten und Gesprächszweige wechseln.", "Edit existing messages and switch conversation branches."],
  ["messageDelete", "Nachrichten löschen", "Delete messages", "Ausgewählte Nachrichten dauerhaft löschen.", "Permanently delete selected messages."],
  ["threadCreate", "Threads erstellen", "Create threads", "Neue Unterhaltungen bei Bots und Gruppen erstellen.", "Create conversations in bots and groups."],
  ["threadEdit", "Threads verwalten", "Manage threads", "Threads umbenennen, archivieren, verschieben und deren Modelle ändern.", "Rename, archive and move threads, and change their models."],
  ["threadDelete", "Threads löschen", "Delete threads", "Threads einschließlich ihrer Nachrichten dauerhaft löschen.", "Permanently delete threads including their messages."],
  ["approvals", "Bot-Aktionen bestätigen oder ablehnen", "Approve or deny bot actions", "Bestätigungsanfragen von Bots beantworten. Aktionen werden dadurch nicht automatisch genehmigt.", "Answer bot approval requests. This does not automatically approve actions."],
  ["botCreate", "Bots erstellen", "Create bots", "Neue Bots anlegen oder importieren.", "Create or import bots."],
  ["botEdit", "Bots konfigurieren", "Configure bots", "Bot-Profile, Modelle, Anweisungen und Ausführungsregeln ändern.", "Change bot profiles, models, instructions and execution rules."],
  ["botDelete", "Bots löschen", "Delete bots", "Bots einschließlich ihrer Unterhaltungen löschen.", "Delete bots and their conversations."],
  ["groups", "Gruppen verwalten", "Manage groups", "Gruppen erstellen und ihre Einstellungen ändern.", "Create groups and change their settings."],
  ["groupDelete", "Gruppen löschen", "Delete groups", "Gruppen einschließlich ihrer Unterhaltungen löschen.", "Delete groups and their conversations."],
  ["teams", "Teams verwalten", "Manage teams", "Teams, Zuordnungen und gemeinsame Anweisungen verwalten.", "Manage teams, assignments and shared instructions."],
  ["routines", "Routinen verwalten", "Manage routines", "Routinen ansehen, erstellen, ändern und löschen.", "View, create, edit and delete routines."],
  ["routineRun", "Routinen ausführen", "Run routines", "Routinen starten oder laufende Routinen abbrechen.", "Start routines or cancel running routines."],
  ["attachments", "Dateien und Bilder übertragen", "Transfer files and images", "Anhänge hochladen, ansehen und herunterladen.", "Upload, view and download attachments."],
  ["export", "Chats exportieren", "Export chats", "Unterhaltungen als Dateien exportieren.", "Export conversations as files."],
  ["voice", "Sprachfunktionen verwenden", "Use voice features", "Sprachausgabe und sprachbezogene API-Funktionen verwenden.", "Use speech output and voice APIs."],
  ["connectors", "Verbundene Apps verwalten", "Manage connected apps", "Apps verbinden, Verbindungen ansehen und trennen.", "Connect apps, view connections and disconnect them."],
  ["memory", "Bot-Erinnerungen verwalten", "Manage bot memory", "Bot-Erinnerungen lesen, ändern und löschen.", "Read, change and delete bot memory."],
  ["skills", "Bot-Skills verwalten", "Manage bot skills", "Skills und Werkzeuge lesen, hinzufügen, ändern und löschen.", "Read, add, change and delete skills and tools."],
  ["promptInspector", "Modell-Eingaben einsehen", "Inspect model inputs", "Die vollständigen an Modelle gesendeten Eingaben einsehen.", "Inspect complete inputs sent to models."],
  ["profile", "Gemeinsames Profil ändern", "Change shared profile", "Name, E-Mail und Profilinformationen des Workspace ändern.", "Change workspace name, email and profile information."],
  ["providers", "Anbieter und Modelle verwalten", "Manage providers and models", "Anbieter-Zugangsdaten, Modelle und Modellvorgaben verwalten.", "Manage provider credentials, models and model defaults."],
  ["engines", "Engines verwalten", "Manage engines", "Engines konfigurieren und ihre Anmeldung verwalten.", "Configure engines and manage their sign-in."],
  ["browser", "Browser verwalten", "Manage browser", "Browser-Einstellungen, Profile und Browser-Steuerung verwalten.", "Manage browser settings, profiles and controls."],
  ["localVm", "Lokale VM verwalten", "Manage local VM", "Die lokale virtuelle Maschine einrichten und steuern.", "Configure and control the local virtual machine."],
  ["backups", "Datensicherungen verwalten", "Manage backups", "Workspace-Datensicherungen exportieren und wiederherstellen.", "Export and restore workspace backups."],
  ["usage", "Nutzung und Aktivitäten einsehen", "View usage and activity", "Verbrauch, Kosten und Verwaltungsaktivitäten einsehen.", "View usage, costs and administration activity."],
  ["budgets", "Kostenregeln verwalten", "Manage cost rules", "Budgets, Preislisten und Abrechnungseinstellungen ändern.", "Change budgets, price lists and billing settings."],
  ["webhooks", "Webhooks verwalten", "Manage webhooks", "Externe Auslöser erstellen, ändern und löschen.", "Create, change and delete external triggers."],
  ["cloudDesktop", "Computeransicht bedienen", "Control computer view", "Den interaktiven Computerbildschirm öffnen und bedienen.", "Open and control the interactive computer screen."],
  ["workspace", "Weitere Workspace-Einstellungen verwalten", "Manage other workspace settings", "Weitere administrative Einstellungen und Workspace-Funktionen verwalten.", "Manage remaining administrative settings and workspace operations."],
] as const;
export type PermissionId = typeof PERMISSIONS[number][0];
export type PermissionMap = Record<PermissionId, boolean>;
export type AccessMode = "admin" | "client" | "custom";
export const CHAT_PERMISSIONS = new Set<PermissionId>(["chatRead", "chatSend", "chatEdit", "threadCreate", "threadEdit", "approvals", "attachments", "voice"]);
export function presetPermissions(mode: AccessMode): PermissionMap {
  return Object.fromEntries(PERMISSIONS.map(([id]) => [id, mode === "admin" || (mode === "client" && CHAT_PERMISSIONS.has(id))])) as PermissionMap;
}
export function validPermissions(value: unknown): value is PermissionMap {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  const map = value as Record<string, unknown>;
  return Object.keys(map).length === PERMISSIONS.length && PERMISSIONS.every(([id]) => typeof map[id] === "boolean");
}
export function effectivePermissions(mode: AccessMode, custom?: Partial<PermissionMap>, cloudDesktop?: boolean): PermissionMap {
  const result = presetPermissions(mode);
  if (mode === "custom") for (const [id] of PERMISSIONS) result[id] = custom?.[id] === true;
  // Old records keep their explicit screen-control flag until the owner chooses a preset.
  if (cloudDesktop !== undefined) result.cloudDesktop = cloudDesktop;
  return result;
}
/** Infrastructure never grants administration of pairing tokens to a custom device. */
export function permissionFor(method: string, path: string): PermissionId | "connection" | "hostOnly" {
  if (/^\/api\/(?:internal|testing)(?:\/|$)/.test(path)) return "hostOnly";
  if (/^\/api\/auth\/(?:session|stream-ticket|logout)$/.test(path)) return "connection";
  if (/^\/api\/(?:auth|devices)(?:\/|$)/.test(path)) return "hostOnly";
  if (/^\/api\/(?:health|edition|brand|companion\/access|companion\/endpoints)$/.test(path)) return "connection";
  if (path === "/api/events") return "chatRead";
  if (/\/(?:computer)\//.test(path)) return "cloudDesktop";
  if (/\/message-selection$|\/messages\/delete$/.test(path)) return "messageDelete";
  if (/\/prompt-inspector(?:\/|$)/.test(path)) return "promptInspector";
  if (path.endsWith("/export") && path.includes("/threads/")) return "export";
  if (/^\/api\/(?:attachments|files)(?:\/|$)/.test(path) || /\/messages\/[^/]+\/(?:image|file)$/.test(path)) return "attachments";
  if (/^\/api\/(?:tts|speech|transcription)(?:\/|$)/.test(path)) return "voice";
  if (/\/memory(?:\/|$)/.test(path)) return "memory";
  if (/\/(?:skills|tools|mcp)(?:\/|$)/.test(path)) return "skills";
  if (/\/(?:respond|always-allow)$|\/(?:cards|secret-cards)\//.test(path)) return "approvals";
  if (/^\/api\/connectors(?:\/|$)|\/connector-cards\//.test(path)) return "connectors";
  if (/^\/api\/(?:routines|routine-runs)(?:\/|$)/.test(path)) return /\/(?:run|cancel)$/.test(path) ? "routineRun" : "routines";
  if (/\/tasks(?:\/[^/]+)?$/.test(path)) return method === "DELETE" ? "threadDelete" : method === "POST" && path.endsWith("/tasks") ? "threadCreate" : "threadEdit";
  if (/\/messages(?:\/|$)|\/(?:read|interrupt|queue)(?:\/|$)/.test(path)) return method === "GET" ? "chatRead" : /\/edit$|\/reactions$/.test(path) ? "chatEdit" : "chatSend";
  if (/\/(?:active-branch|compact)$/.test(path)) return "chatEdit";
  if (/^\/api\/(?:bots|groups)\/[^/]+$/.test(path)) return method === "DELETE" ? (path.startsWith("/api/bots/") ? "botDelete" : "groupDelete") : path.startsWith("/api/bots/") ? "botEdit" : "groups";
  if (path === "/api/bots") return method === "GET" ? "chatRead" : "botCreate";
  if (path === "/api/groups") return method === "GET" ? "chatRead" : "groups";
  if (/^\/api\/(?:search|threads)(?:\/|$)/.test(path)) return method === "GET" ? "chatRead" : method === "DELETE" ? "threadDelete" : "threadEdit";
  if (/^\/api\/(?:teams|team-map|sidebar-sections|org-library)(?:\/|$)/.test(path)) return method === "GET" && path === "/api/team-map" ? "chatRead" : "teams";
  if (/^\/api\/(?:usage|usage\.csv|activity|admin-activity)(?:\/|$)/.test(path)) return "usage";
  if (/^\/api\/(?:budgets|billing|prices|pricing)(?:\/|$)/.test(path)) return "budgets";
  if (/^\/api\/webhooks(?:\/|$)/.test(path)) return "webhooks";
  if (/^\/api\/(?:backups|backup|workspace-backups|workspace-backup)(?:\/|$)/.test(path)) return "backups";
  if (/^\/api\/(?:local-computer|local-vm)(?:\/|$)/.test(path)) return "localVm";
  if (/^\/api\/(?:browser|browser-profiles|host-browser|browser-engine)(?:\/|$)/.test(path)) return "browser";
  if (/^\/api\/(?:instances|engines)(?:\/|$)/.test(path)) return "engines";
  if (/^\/api\/(?:providers|models|keys|bot-defaults|config\/providers|config\/models)(?:\/|$)/.test(path)) return "providers";
  if (/^\/api\/config\/profile$/.test(path)) return "profile";
  // Config writes are checked field by field after parsing their body.
  if (path === "/api/config") return "connection";
  if (path.startsWith("/api/bots/")) return "botEdit";
  if (path.startsWith("/api/groups/")) return "groups";
  return "workspace";
}
export function configPermissionDenial(body: unknown, grants: PermissionMap): string | null {
  if (!body || typeof body !== "object" || Array.isArray(body)) return "Invalid configuration body";
  for (const [key, value] of Object.entries(body)) {
    if (key === "features" && value && typeof value === "object") {
      for (const feature of Object.keys(value)) {
        const required: PermissionId = feature === "browser" ? "browser" : feature === "skillAuthoring" ? "skills" : "workspace";
        if (!grants[required]) return `Berechtigung gesperrt / Permission blocked: ${required}`;
      }
      continue;
    }
    let id: PermissionId = "workspace";
    if (["profile", "aboutMe"].includes(key)) id = "profile";
    else if (["instances", "anthropic", "openaiCompat", "xai", "mistral", "opencodeGo", "defaultModelSelection", "newBotDefaults", "tts", "stt"].includes(key)) id = "providers";
    else if (["localVm"].includes(key)) id = "localVm";
    else if (["browserProfiles", "expectedBrowserProfiles", "hostBrowser"].includes(key)) id = "browser";
    else if (["budgets", "billing"].includes(key)) id = "budgets";
    if (!grants[id]) return `Berechtigung gesperrt / Permission blocked: ${id}`;
  }
  return null;
}
export function permissionDenial(method: string, path: string, mode: AccessMode, permissions?: Partial<PermissionMap>, cloudDesktop?: boolean): string | null {
  const id = permissionFor(method, path);
  if (id === "connection") return null;
  if (id === "hostOnly") return "Diese Funktion wird ausschließlich am Host verwaltet / Host administration only";
  return effectivePermissions(mode, permissions, cloudDesktop)[id] ? null : `Berechtigung gesperrt / Permission blocked: ${id}`;
}
