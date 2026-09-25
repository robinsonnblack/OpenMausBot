// The memory panel's client side: the wire types the server's memory
// routes answer with, the calls, and the pure wording helpers the panel
// renders from — kept here so the sentences can be tested without React.
import { ApiError, api } from "@/state/store";
import { activeLocale, t } from "@/lib/i18n";

export const MEMORY_INDEX = "MEMORY.md";

export interface MemoryCapacity {
  lines: number;
  bytes: number;
  maxLines: number;
  maxBytes: number;
  loadedLines: number;
  loadedBytes: number;
  truncated: boolean;
  hash: string;
}

export interface MemoryFileInfo {
  path: string;
  name: string;
  bytes: number;
  modifiedAt: number;
}

export interface MemoryOverview {
  botId: string;
  workspacePath: string;
  index: MemoryCapacity;
  topics: MemoryFileInfo[];
  logs: MemoryFileInfo[];
}

export interface MemoryDoc {
  path: string;
  text: string;
  hash: string;
  exists: boolean;
}

export type MemoryActor = "bot" | "person" | "import";

/** A journal row as the server sends it: no prior text (that stays on
 * the server for the revert), but the chat title when the thread is known. */
export interface MemoryJournalRow {
  id: string;
  at: number;
  botId: string;
  path: string;
  actor: MemoryActor;
  via: string;
  threadId?: string;
  threadTitle?: string;
  kind: "created" | "edited" | "deleted";
  beforeHash: string | null;
  afterHash: string | null;
  diff: string;
  added: number;
  removed: number;
  canRevert: boolean;
  revertUnavailableReason?: string;
}

export type SaveResult =
  | { ok: true; doc: MemoryDoc; overview: MemoryOverview }
  | { ok: false; conflict: true; current: string; currentHash: string };

export function fetchMemoryOverview(botId: string): Promise<MemoryOverview> {
  return api(`/api/bots/${botId}/memory`);
}

export function fetchMemoryDoc(botId: string, path: string): Promise<MemoryDoc> {
  return api(`/api/bots/${botId}/memory/file?path=${encodeURIComponent(path)}`);
}

/** A 409 is an answer, not a failure: the bot wrote this file after the
 * editor opened it, and the panel has to show both versions. Every other
 * refusal throws the way api() does. */
export async function saveMemoryDoc(botId: string, path: string, text: string, expectedHash: string | undefined): Promise<SaveResult> {
  const response = await fetch(`/api/bots/${botId}/memory/file`, {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ path, text, expectedHash }),
  });
  // SAFETY: the server's JSON is narrowed field by field below
  const body = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (response.status === 409 && typeof body.current === "string" && typeof body.currentHash === "string") {
    return { ok: false, conflict: true, current: body.current, currentHash: body.currentHash };
  }
  if (!response.ok) throw new ApiError(typeof body.error === "string" ? body.error : `${response.status} ${response.statusText}`, response.status);
  // SAFETY: a 200 from PUT …/memory/file carries the saved document and the refreshed overview
  const saved = body as unknown as MemoryDoc & { overview: MemoryOverview };
  return { ok: true, doc: { path: saved.path, text: saved.text, hash: saved.hash, exists: saved.exists }, overview: saved.overview };
}

export function deleteMemoryDoc(botId: string, path: string): Promise<{ overview: MemoryOverview }> {
  return api(`/api/bots/${botId}/memory/file?path=${encodeURIComponent(path)}`, { method: "DELETE" });
}

export async function fetchMemoryJournal(botId: string, limit = 50): Promise<MemoryJournalRow[]> {
  const result: { entries: MemoryJournalRow[] } = await api(`/api/bots/${botId}/memory/journal?limit=${limit}`);
  return result.entries;
}

export function revertMemoryChange(botId: string, entryId: string): Promise<MemoryDoc & { overview: MemoryOverview }> {
  return api(`/api/bots/${botId}/memory/journal/${encodeURIComponent(entryId)}/revert`, { method: "POST" });
}

export type MemoryOpenTarget = "obsidian" | "folder";

export function openMemoryLocation(botId: string, target: MemoryOpenTarget): Promise<{ ok: boolean; workspacePath: string }> {
  return api(`/api/bots/${botId}/memory/open`, { method: "POST", body: JSON.stringify({ target }) });
}

// ── wording ───────────────────────────────────────────────────────────

export function formatBytes(bytes: number): string {
  return bytes < 1024 ? `${bytes} B` : `${Math.round(bytes / 102.4) / 10} KB`;
}

/** "just now", "3 min ago", "2 hr ago", "yesterday", "Sep 3" — the
 * journal is read for what happened recently, so recent rows get the
 * finer grain. */
export function relativeTime(at: number, now = Date.now()): string {
  const seconds = Math.max(0, Math.round((now - at) / 1000));
  if (seconds < 45) return t("botMemory.time.justNow");
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return t("botMemory.time.minutesAgo", { count: minutes });
  const hours = Math.round(minutes / 60);
  if (hours < 24) return t("botMemory.time.hoursAgo", { count: hours });
  const days = Math.round(hours / 24);
  if (days === 1) return t("botMemory.time.yesterday");
  if (days < 7) return t("botMemory.time.daysAgo", { count: days });
  return new Date(at).toLocaleDateString(activeLocale(), { month: "short", day: "numeric" });
}

export type CapacityLevel = "ok" | "near" | "over";

export interface CapacityStatus {
  level: CapacityLevel;
  /** 0–1 of the line budget and of the byte budget; can exceed 1. */
  lineShare: number;
  byteShare: number;
  /** The one plain sentence the gauge always shows. */
  sentence: string;
  /** Only when over: how much is not loading, in the person's terms. */
  warning: string | null;
}

/** The gauge's sentence is the feature: it names the cut loadMemory makes
 * silently. "Near" starts at 80% of either budget so the person sees it
 * coming rather than finding out after a note stopped loading. */
export function capacityStatus(index: MemoryCapacity): CapacityStatus {
  const lineShare = index.maxLines ? index.lines / index.maxLines : 0;
  const byteShare = index.maxBytes ? index.bytes / index.maxBytes : 0;
  const level: CapacityLevel = index.truncated ? "over" : Math.max(lineShare, byteShare) >= 0.8 ? "near" : "ok";
  const sentence = t("botMemory.capacitySentence", { lines: index.lines, maxLines: index.maxLines, bytes: formatBytes(index.bytes), maxBytes: formatBytes(index.maxBytes) });
  let warning: string | null = null;
  if (index.truncated) {
    const missingLines = index.lines - index.loadedLines;
    warning = missingLines > 0
      ? t(missingLines === 1 ? "botMemory.warningOneLine" : "botMemory.warningLines", { lines: index.lines, loadedLines: index.loadedLines, missingLines })
      : t("botMemory.warningBytes", { bytes: formatBytes(index.bytes), loadedBytes: formatBytes(index.loadedBytes) });
  }
  return { level, lineShare, byteShare, sentence, warning };
}

function fileLabel(path: string): string {
  if (path === MEMORY_INDEX) return "MEMORY.md";
  if (path.startsWith("memory/log/")) return t("botMemory.logLabel", { name: path.slice("memory/log/".length).replace(/\.md$/, "") });
  return path.replace(/^memory\//, "").replace(/\.md$/, "");
}

/** "Scout added 2 lines to MEMORY.md", "You removed the clients topic",
 * "Scout rewrote 3 lines in MEMORY.md". Subject first, in the person's
 * words: what the row means, not which fields it has. */
export function journalSummary(row: MemoryJournalRow, botName: string): string {
  const who = row.actor === "bot" ? t("botMemory.actorBot", { bot: botName }) : row.actor === "import" ? t("botMemory.actorImport") : t("botMemory.actorYou");
  const file = fileLabel(row.path);
  const isIndex = row.path === MEMORY_INDEX;
  const topic = isIndex || row.path.startsWith("memory/log/") ? file : t("botMemory.topicLabel", { name: file });
  if (row.kind === "created") return t(row.actor === "person" ? "botMemory.journalCreatedYou" : "botMemory.journalCreated", { who, topic }) + (row.added ? t("botMemory.journalWithLines", { lines: plural(row.added) }) : "");
  if (row.kind === "deleted") return t(row.actor === "person" ? "botMemory.journalDeletedYou" : "botMemory.journalDeleted", { who, topic });
  if (row.added && !row.removed) return t(row.actor === "person" ? "botMemory.journalAddedYou" : "botMemory.journalAdded", { who, lines: plural(row.added), topic });
  if (row.removed && !row.added) return t(row.actor === "person" ? "botMemory.journalRemovedYou" : "botMemory.journalRemoved", { who, lines: plural(row.removed), topic });
  return t(row.actor === "person" ? "botMemory.journalRewroteYou" : "botMemory.journalRewrote", { who, lines: plural(Math.max(row.added, row.removed)), topic });
}

function plural(count: number): string {
  return t(count === 1 ? "botMemory.oneLine" : "botMemory.manyLines", { count });
}

/** Where the change came from, as the second clause of the row. */
export function journalSource(row: MemoryJournalRow): string | null {
  if (row.via === "revert") return t("botMemory.sourceUndo");
  if (row.via === "disk") return t("botMemory.sourceDisk");
  if (row.threadTitle) return t("botMemory.sourceChat", { title: row.threadTitle });
  if (row.actor === "bot") return t("botMemory.sourceTask");
  if (row.via === "ui") return t("botMemory.sourceSettings");
  if (row.via === "api") return t("botMemory.sourceApi");
  return null;
}

export function fileManagerLabel(platform: string | undefined): string {
  if (platform === "darwin") return t("botMemory.showFinder");
  if (platform === "win32") return t("botMemory.showExplorer");
  return t("botMemory.showFileManager");
}

/** A new topic's file name from whatever the person typed: spaces and
 * odd punctuation become dashes, the .md is added, and anything that
 * still fails the server's name gate is refused up front. */
export function topicFileName(input: string): string | null {
  const stem = input.trim().replace(/\.md$/i, "").replace(/[^\w .-]+/g, "-").replace(/^[^\w]+/, "").trim();
  if (!stem) return null;
  return `${stem}.md`;
}
