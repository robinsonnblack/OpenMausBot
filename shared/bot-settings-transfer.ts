/** Only persistent settings: never copy ids, transcripts, credentials or runtime state. */
export const TRANSFER_SETTINGS = {
  name: "Name", title: "Title", description: "Description", soul: "Standing instructions",
  color: "Color", mascotBody: "Mascot body", avatarUrl: "Profile picture", avatarCrop: "Picture crop",
  modelSelection: "Model and reasoning effort", computer: "Computer", cloudBackend: "Cloud backend",
  autoStartVps: "Start VPS automatically", cwd: "Working folder", browser: "Browser access",
  browserProfile: "Browser profile", composio: "Connected apps", connectorTools: "Connected app tools",
  mcpServers: "MCP servers", approvalMode: "Approval level", alwaysAllow: "Always allowed tools",
  approvePeerComms: "Ask before contacting bots", peers: "Allowed bots", parkDirectMessages: "Queue direct messages",
  voice: "Voice", speakReplies: "Read replies aloud", voiceNotes: "Voice notes", notifications: "Notifications",
  section: "Team", managedSections: "Managed teams",
} as const;
export type TransferSetting = keyof typeof TRANSFER_SETTINGS;
const defaults: Record<string, unknown> = {
  title: "", description: "", soul: "", mascotBody: "cursor", avatarUrl: null, avatarCrop: "mascot",
  computer: null, cloudBackend: "box", autoStartVps: false, cwd: null, browser: true, browserProfile: null,
  composio: true, connectorTools: null, mcpServers: null, approvalMode: "ask", alwaysAllow: [],
  approvePeerComms: false, peers: null, parkDirectMessages: false, voice: "", speakReplies: false,
  voiceNotes: true, notifications: true, section: null, managedSections: [],
};
export function transferableSettings(source: object): Record<string, unknown> {
  const record = source as Record<string, unknown>;
  return Object.fromEntries(Object.keys(TRANSFER_SETTINGS).map(key => [key,
    record[key] ?? (key === "approvalMode" && record.autoApprove ? "auto" : defaults[key])]));
}
export function settingsTransferPatch(snapshot: Record<string, unknown>, fields: string[]) {
  if (!fields.length || fields.some(key => !Object.hasOwn(TRANSFER_SETTINGS, key))) throw Error("Invalid setting selection");
  const patch = Object.fromEntries(fields.map(key => [key, structuredClone(snapshot[key])]));
  // Consent belongs to this explicit, reviewed copy, never to the source bot's old consent.
  if (fields.includes("approvalMode")) patch.confirmFullAccess = patch.approvalMode === "full";
  if (fields.some(key => ["approvalMode", "computer", "modelSelection", "alwaysAllow"].includes(key))) patch.acknowledgeLocalAuto = true;
  if (fields.includes("peers")) patch.acknowledgePeerScope = true;
  if (fields.includes("managedSections")) patch.acknowledgePeerScope = true;
  return patch;
}
