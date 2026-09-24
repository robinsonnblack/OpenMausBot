import fs from "node:fs";
import path from "node:path";
import { DatabaseSync } from "node:sqlite";
import { spawn } from "node:child_process";
import { createInterface } from "node:readline";
import { Worker } from "node:worker_threads";
import { makeIndex, mayContain, nativeThreadIds } from "./deletion-scan-index.mjs";
const fileIndexes = /* @__PURE__ */ new Map(), invalidations = /* @__PURE__ */ new Map();
function invalidate(p) {
  fileIndexes.delete(p);
  invalidations.set(p, (invalidations.get(p) ?? 0) + 1);
}
function indexed(p) {
  const entry = fileIndexes.get(p);
  if (!entry) return null;
  try {
    const stat = fs.statSync(p);
    if (entry.size === stat.size && entry.mtime === stat.mtimeMs) return entry;
  } catch {
  }
  fileIndexes.delete(p);
  return null;
}
function readCandidate(p, clean, { memory = false } = {}) {
  const entry = indexed(p);
  if (entry && !clean.mayContainIndex(entry, memory)) return null;
  const bytes = fs.readFileSync(p);
  if (!entry) {
    const stat = fs.statSync(p);
    fileIndexes.set(p, { ...makeIndex(bytes), size: stat.size, mtime: stat.mtimeMs, nativeIds: nativeThreadIds(bytes) });
  }
  return bytes;
}
const exists = (p) => fs.existsSync(p);
const uuid = /^[a-zA-Z0-9_-]+$/;
const fail = (status, message) => Object.assign(new Error(message), { status });
function walk(root) {
  if (!exists(root)) return [];
  const out = [], pending = [path.resolve(root)];
  while (pending.length) {
    const p = pending.pop(), s = fs.lstatSync(p);
    if (s.isSymbolicLink()) continue;
    if (s.isDirectory()) {
      for (const name of fs.readdirSync(p)) pending.push(path.join(p, name));
    } else if (s.isFile()) out.push(p);
  }
  return out;
}
function removeFile(p) {
  invalidate(p);
  if (exists(p) && fs.lstatSync(p).isFile()) fs.unlinkSync(p);
}
function write(p, value) {
  invalidate(p);
  fs.writeFileSync(p, value, { encoding: "utf8", mode: 384 });
}
function rechain(messages, activeLeaf, ids) {
  const byId = new Map(messages.map((m) => [m.id, m])), memo = /* @__PURE__ */ new Map();
  function parent(id) {
    const seen = [], visited = /* @__PURE__ */ new Set();
    let cur = id;
    while (cur && ids.has(cur)) {
      if (memo.has(cur)) {
        cur = memo.get(cur);
        break;
      }
      if (visited.has(cur)) throw fail(500, "Invalid conversation branch");
      seen.push(cur);
      visited.add(cur);
      cur = byId.get(cur)?.parentId ?? null;
    }
    for (const item of seen) memo.set(item, cur);
    return cur ?? null;
  }
  const kept = messages.filter((m) => !ids.has(m.id)).map((m) => {
    const result = { ...m, parentId: parent(m.parentId) };
    if (ids.has(m.replyToId)) {
      delete result.replyToId;
      delete result.replyTo;
      delete result.replyTarget;
    }
    return result;
  });
  return { messages: kept, activeLeafId: parent(activeLeaf) };
}
function scrubber(ids, deleted, threadTitles = []) {
  const text = [...new Set(deleted.map((m) => m.text).filter((t) => typeof t === "string" && t.trim().length >= 16))].sort((a, b) => b.length - a.length);
  const escape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = (values, flags = "") => new RegExp(values.length ? values.map(escape).join("|") : "(?!)", flags);
  const idValues = [...ids], uuidIds = new Set(idValues.filter((id) => /^[\da-f]{8}-[\da-f]{4}-[\da-f]{4}-[\da-f]{4}-[\da-f]{12}$/i.test(id)));
  const otherIds = idValues.filter((id) => !uuidIds.has(id)), idPattern = pattern(otherIds), textPattern = pattern(text, "g");
  const copies = [...new Set(text.flatMap((t) => [t, JSON.stringify(t).slice(1, -1), JSON.stringify(JSON.stringify(t)).slice(1, -1)]))];
  const copyPattern = pattern(copies), byteCopyPattern = pattern(copies.map((t) => Buffer.from(t, "utf8").toString("latin1")));
  const hasId = (s) => {
    if (idValues.length <= 4) return idValues.some((id) => s.includes(id));
    if (otherIds.length && idPattern.test(s)) return true;
    let i = -1;
    while ((i = s.indexOf("-", i + 1)) >= 0) {
      if (i >= 8 && s[i + 5] === "-" && s[i + 10] === "-" && s[i + 15] === "-" && uuidIds.has(s.slice(i - 8, i + 28))) return true;
    }
    return false;
  };
  const contains = (s) => hasId(s) || copyPattern.test(s);
  const containsBuffer = (buffer) => {
    const bytes = buffer.toString("latin1");
    return hasId(bytes) || byteCopyPattern.test(bytes);
  };
  const cleanText = (s) => s.replace(textPattern, "");
  function clean(value) {
    if (typeof value === "string") return hasId(value) ? "" : cleanText(value);
    if (Array.isArray(value)) return value.map(clean).filter((x) => x !== void 0);
    if (!value || typeof value !== "object") return value;
    if (ids.has(value.id) || ids.has(value.message_id) || ids.has(value.messageId)) return void 0;
    const result = {};
    for (const [key, v] of Object.entries(value)) {
      if (["parentId", "activeLeafId"].includes(key)) {
        result[key] = v;
        continue;
      }
      if (["replyToId", "pinnedMessageId"].includes(key) && ids.has(v)) continue;
      const next = clean(v);
      if (next !== void 0) result[key] = next;
    }
    return result;
  }
  const hasMemorySource = (s) => threadTitles.some((title) => title && s.includes('from chat "' + title + '"'));
  const memorySourceBytes = threadTitles.filter(Boolean).map((title) => Buffer.from('from chat "' + title + '"', "utf8"));
  const hasMemorySourceBuffer = (buffer) => memorySourceBytes.some((source) => buffer.includes(source));
  const needles = [...idValues, ...copies].map((value) => Buffer.from(value, "utf8"));
  const mayContainIndex = (index, memory = false) => needles.some((needle) => mayContain(index, needle)) || memory && memorySourceBytes.some((needle) => mayContain(index, needle));
  const cleanMemory = (s) => cleanText(s).split("\n").filter((line) => !hasId(line) && !hasMemorySource(line)).join("\n");
  return { clean, cleanText, cleanMemory, hasId, contains, containsBuffer, hasMemorySource, hasMemorySourceBuffer, mayContainIndex };
}
function tables(db) {
  return new Set(db.prepare("SELECT name FROM sqlite_master WHERE type='table'").all().map((r) => r.name));
}
function purgeDatabase(db, threadId, ids, clean, { primary = false } = {}) {
  const names = tables(db);
  if (!names.has("messages")) return { changes: [] };
  db.exec("PRAGMA secure_delete=ON");
  const rows = db.prepare("SELECT thread_id,id,json FROM messages").all();
  const target = rows.filter((r) => r.thread_id === threadId).map((r) => JSON.parse(r.json));
  const leaf = names.has("thread_state") ? db.prepare("SELECT active_leaf_id AS id FROM thread_state WHERE thread_id=?").get(threadId)?.id ?? null : null;
  const next = rechain(target, leaf, ids), replacement = new Map(next.messages.map((m) => [m.id, m]));
  const changes = [];
  let messagesDirty = false, memoryDirty = false, otherDirty = false;
  db.exec("BEGIN IMMEDIATE");
  try {
    const del = db.prepare("DELETE FROM messages WHERE thread_id=? AND id=?");
    const update = db.prepare("UPDATE messages SET text=?,json=? WHERE thread_id=? AND id=?");
    for (const row of rows) {
      if (ids.has(row.id)) {
        del.run(row.thread_id, row.id);
        messagesDirty = true;
        continue;
      }
      if (row.thread_id !== threadId && !clean.contains(row.json)) continue;
      const original = JSON.parse(row.json), message = clean.clean(replacement.get(row.id) ?? original);
      if (!message) {
        del.run(row.thread_id, row.id);
        messagesDirty = true;
        continue;
      }
      const json = JSON.stringify(message);
      if (json !== row.json) {
        update.run(message.text ?? null, json, row.thread_id, row.id);
        messagesDirty = true;
        changes.push({ threadId: row.thread_id, message });
      }
    }
    if (names.has("thread_state") && leaf !== next.activeLeafId) {
      db.prepare("UPDATE thread_state SET active_leaf_id=? WHERE thread_id=?").run(next.activeLeafId, threadId);
      otherDirty = true;
    }
    if (names.has("chat_followups")) {
      const updateFollowup = db.prepare("UPDATE chat_followups SET payload=? WHERE id=?");
      for (const r of db.prepare("SELECT id,thread_id,payload FROM chat_followups").all()) {
        if (r.thread_id === threadId) {
          db.prepare("DELETE FROM chat_followups WHERE id=?").run(r.id);
          otherDirty = true;
          continue;
        }
        if (!clean.contains(r.payload)) continue;
        const value = JSON.stringify(clean.clean(JSON.parse(r.payload)) ?? {});
        if (value !== r.payload) {
          updateFollowup.run(value, r.id);
          otherDirty = true;
        }
      }
    }
    if (names.has("memory_files")) {
      const updateMemory = db.prepare("UPDATE memory_files SET text=?,mtime_ms=0,bytes=0 WHERE bot_id=? AND path=?");
      for (const r of db.prepare("SELECT bot_id,path,text FROM memory_files").all()) {
        if (!clean.contains(r.text) && !clean.hasMemorySource(r.text)) continue;
        const nextText = clean.cleanMemory(r.text);
        if (nextText !== r.text) {
          updateMemory.run(nextText, r.bot_id, r.path);
          memoryDirty = true;
        }
      }
    }
    db.exec("COMMIT");
  } catch (e) {
    db.exec("ROLLBACK");
    throw e;
  }
  for (const [name, dirty] of [["messages_fts", messagesDirty], ["memory_fts", memoryDirty]]) if (dirty && names.has(name)) db.exec(`INSERT INTO ${name}(${name}) VALUES ('rebuild')`);
  if (messagesDirty || memoryDirty || otherDirty) {
    db.exec("PRAGMA wal_checkpoint(TRUNCATE)");
    db.exec("VACUUM");
    db.exec("PRAGMA wal_checkpoint(TRUNCATE)");
  }
  return { changes, activeLeafId: next.activeLeafId };
}
async function deleteNativeThreads(cli, ids) {
  if (!ids.length) return;
  const proc = spawn(cli, ["app-server"], { windowsHide: true, stdio: ["pipe", "pipe", "pipe"] });
  let seq = 0;
  const pending = /* @__PURE__ */ new Map();
  proc.stderr.on("data", () => {
  });
  const send = (o) => proc.stdin.write(JSON.stringify(o) + "\n");
  createInterface({ input: proc.stdout }).on("line", (line) => {
    let m;
    try {
      m = JSON.parse(line);
    } catch {
      return;
    }
    const p = pending.get(m.id);
    if (p) {
      pending.delete(m.id);
      m.error ? p.reject(new Error(m.error.message)) : p.resolve(m.result);
    }
  });
  proc.on("error", (e) => {
    for (const p of pending.values()) p.reject(e);
    pending.clear();
  });
  const request = (method, params) => new Promise((resolve, reject) => {
    const id = ++seq, timer = setTimeout(() => {
      pending.delete(id);
      reject(new Error("Native cleanup timed out"));
    }, 2e4);
    pending.set(id, { resolve: (r) => {
      clearTimeout(timer);
      resolve(r);
    }, reject: (e) => {
      clearTimeout(timer);
      reject(e);
    } });
    send({ id, method, params });
  });
  try {
    await request("initialize", { clientInfo: { name: "openmausbot_message_deletion", version: "1" }, capabilities: { experimentalApi: true } });
    send({ method: "initialized" });
    for (const id of ids) await request("thread/delete", { threadId: id });
  } finally {
    proc.stdin.end();
  }
}
function createMessageDeletion({ store, getDb, dataDir, broadcast, quiesce, clearRuntime, configFile, purgeCaptures }) {
  let busy = false;
  try {
    const settings = exists(configFile) ? JSON.parse(fs.readFileSync(configFile, "utf8")) : {};
    const files = new Set([...walk(dataDir), ...(settings.archiveRoots ?? []).flatMap(walk), ...settings.archiveFiles ?? []].filter((p) => /\.(json|ndjson|jsonl|md|txt)$/i.test(p)));
    if (settings.codexStateDb && exists(settings.codexStateDb)) {
      const native = new DatabaseSync(settings.codexStateDb, { readOnly: true }), owned = new Set(settings.nativeThreadIds ?? []);
      try {
        for (const row of native.prepare("SELECT id,originator,rollout_path FROM threads").all()) if (row.originator === "openmausbot" || owned.has(row.id)) files.add(row.rollout_path);
      } finally {
        native.close();
      }
    }
    const versions = new Map([...files].map((p) => [p, invalidations.get(p) ?? 0]));
    const worker = new Worker(new URL("./deletion-scan-index.mjs", import.meta.url), { workerData: { files: [...files] } });
    worker.on("message", (entry) => {
      if ((invalidations.get(entry.path) ?? 0) !== versions.get(entry.path)) return;
      try {
        const stat = fs.statSync(entry.path);
        if (stat.size === entry.size && stat.mtimeMs === entry.mtime) fileIndexes.set(entry.path, entry);
      } catch {
      }
    });
    worker.on("error", () => {
    });
    worker.unref();
  } catch {
  }
  const validThread = (id) => uuid.test(id) && (store.botByThread(id) || store.groupByThread(id));
  async function execute(threadId, selection) {
    if (busy) throw fail(409, "A deletion is already running");
    if (!validThread(threadId)) throw fail(404, "No such conversation");
    const all = store.messagesFor(threadId), available = new Set(all.map((m) => m.id));
    const excluded = new Set(selection?.excludedIds ?? []);
    const values = selection?.all === true ? all.map((m) => m.id).filter((id) => !excluded.has(id)) : selection?.ids;
    if (!Array.isArray(values) || !values.length || values.some((id) => typeof id !== "string" || !uuid.test(id))) throw fail(400, "Select at least one message");
    const ids = new Set(values);
    if ([...ids].some((id) => !available.has(id))) throw fail(404, "A selected message no longer exists");
    const deleted = all.filter((m) => ids.has(m.id));
    const settings = exists(configFile) ? JSON.parse(fs.readFileSync(configFile, "utf8")) : {};
    const owner = store.botByThread(threadId), group = store.groupByThread(threadId);
    const titles = [owner ? store.taskByThread(owner.id, threadId)?.title : null, group?.name].filter(Boolean);
    const clean = scrubber(ids, deleted, titles);
    busy = true;
    const began = performance.now(), timings = {};
    let last = began;
    const mark = (name) => {
      const now = performance.now();
      timings[name] = Math.round((now - last) * 10) / 10;
      last = now;
    };
    try {
      await quiesce(threadId);
      purgeCaptures?.(clean, threadId);
      mark("quiesce");
      const nativeIds = /* @__PURE__ */ new Set(), ownedIds = new Set(settings.nativeThreadIds ?? []), affectedLogs = /* @__PURE__ */ new Set(), nativeScans = /* @__PURE__ */ new Map();
      for (const p of walk(path.join(dataDir, "native"))) {
        const content = readCandidate(p, clean), stat = fs.statSync(p);
        nativeScans.set(p, [stat.size, stat.mtimeMs]);
        const affected = path.basename(p).startsWith(threadId + ".") || content && clean.containsBuffer(content);
        if (affected) affectedLogs.add(p);
        for (const id of indexed(p)?.nativeIds ?? (content ? nativeThreadIds(content) : [])) {
          ownedIds.add(id);
          if (affected) nativeIds.add(id);
        }
      }
      mark("native_logs");
      if (settings.codexStateDb && exists(settings.codexStateDb)) {
        const nativeDb = new DatabaseSync(settings.codexStateDb, { readOnly: true });
        let stored;
        try {
          stored = nativeDb.prepare("SELECT id,originator,rollout_path FROM threads").all().filter((r) => r.originator === "openmausbot" || ownedIds.has(r.id)).filter((r) => nativeIds.has(r.id) || exists(r.rollout_path) && (() => {
            const bytes = readCandidate(r.rollout_path, clean);
            return bytes && clean.containsBuffer(bytes);
          })()).map((r) => r.id);
        } finally {
          nativeDb.close();
        }
        await deleteNativeThreads(settings.codexCli ?? "codex", stored);
        for (const [p] of fileIndexes) if (!exists(p)) invalidate(p);
      }
      mark("native_sessions");
      const result = purgeDatabase(getDb(), threadId, ids, clean, { primary: true });
      mark("primary_database");
      for (const root of settings.archiveRoots ?? []) {
        for (const p of walk(root)) {
          if (path.resolve(p) === path.resolve(path.join(dataDir, "messages.db"))) continue;
          if (path.basename(p) === "messages.db") {
            const archive = new DatabaseSync(p);
            try {
              purgeDatabase(archive, threadId, ids, clean);
            } finally {
              archive.close();
            }
            continue;
          }
          if (/\.(json|ndjson|jsonl|txt|md)$/i.test(p)) scrubFile(p, clean, threadId, ids);
        }
      }
      mark("archives");
      for (const p of settings.archiveFiles ?? []) if (exists(p)) scrubFile(p, clean, threadId, ids);
      mark("archive_files");
      for (const folder of ["events", "native", "memory-journal", "codex-instructions"]) for (const p of walk(path.join(dataDir, folder))) {
        if (affectedLogs.has(p) || path.basename(p).startsWith(threadId + ".")) {
          removeFile(p);
          continue;
        }
        const scanned = nativeScans.get(p);
        if (scanned) {
          const current = fs.statSync(p);
          if (current.size === scanned[0] && current.mtimeMs === scanned[1]) continue;
        }
        const content = readCandidate(p, clean, { memory: folder === "memory-journal" });
        if (content && clean.containsBuffer(content)) removeFile(p);
        else if (folder === "memory-journal") scrubFile(p, clean, threadId, ids);
      }
      mark("diagnostic_logs");
      for (const p of walk(dataDir)) {
        const rel = path.relative(dataDir, p).replaceAll("\\", "/");
        if (/^messages-[\w-]+\.json/.test(rel) || /^(bots|workspaces)\/.*(?:MEMORY\.md|memory\/.*\.md)$/.test(rel)) scrubFile(p, clean, threadId, ids);
      }
      mark("memory_files");
      for (const filename of ["bots.json", "groups.json", "decisions.ndjson", "room-handoffs.json"]) {
        const p = path.join(dataDir, filename);
        if (exists(p)) scrubFile(p, clean, threadId, ids);
      }
      mark("metadata");
      const candidates = /* @__PURE__ */ new Set();
      for (const m of deleted) {
        for (const a of m.attachments ?? []) if (a.path) candidates.add(a.path);
        for (const match of (m.text ?? "").matchAll(/<(?:attached-image|attached-file)[^>]*\bpath="([^"]+)"/g)) candidates.add(match[1]);
      }
      const attachmentRoot = path.resolve(dataDir, "attachments");
      for (const candidate of candidates) {
        const p = path.resolve(candidate), relative = path.relative(attachmentRoot, p);
        if (relative && !relative.startsWith("..") && !path.isAbsolute(relative) && exists(p) && !fs.lstatSync(p).isSymbolicLink()) {
          const canonicalRelative = path.relative(fs.realpathSync(attachmentRoot), fs.realpathSync(p));
          if (canonicalRelative && !canonicalRelative.startsWith("..") && !path.isAbsolute(canonicalRelative)) removeFile(p);
        }
      }
      mark("attachments");
      store.threads.clear();
      for (const bot of store.bots) {
        Object.assign(bot, clean.clean(bot));
        for (const task of bot.tasks ?? []) {
          task.resumeCursors = {};
          task.rewound = true;
          if (ids.has(task.pinnedMessageId)) delete task.pinnedMessageId;
          if (task.threadId === threadId && deleted.some((m) => m.role === "user" && m.text?.trim() && m.text.replace(/\s+/g, " ").trim().startsWith((task.title ?? "").replace(/…$/, "").trim()))) task.title = "Untitled thread";
        }
        bot.resumeCursors = {};
        if (ids.has(bot.pinnedMessageId)) delete bot.pinnedMessageId;
      }
      for (const g of store.groups) {
        Object.assign(g, clean.clean(g));
        if (ids.has(g.pinnedMessageId)) delete g.pinnedMessageId;
        for (const task of g.tasks ?? []) if (ids.has(task.pinnedMessageId)) delete task.pinnedMessageId;
      }
      store.saveBots();
      store.saveGroups();
      clearRuntime(ids, threadId, clean);
      for (const change of result.changes) broadcast({ kind: "message.patch", ...change });
      broadcast({ kind: "messages.deleted", threadId, ids: [...ids], activeLeafId: result.activeLeafId });
      for (const bot of store.bots) store.emit({ type: "bot", botId: bot.id });
      mark("runtime_and_events");
      return { ok: true, deleted: ids.size, ids: [...ids], activeLeafId: result.activeLeafId, elapsedMs: Math.round((performance.now() - began) * 10) / 10, timings };
    } finally {
      busy = false;
    }
  }
  return { get busy() {
    return busy;
  }, execute };
}
function scrubFile(p, clean, threadId, ids) {
  const memory = /\.(md|txt)$/i.test(p);
  const bytes = readCandidate(p, clean, { memory });
  if (!bytes) return;
  if (!clean.containsBuffer(bytes) && !(memory && clean.hasMemorySourceBuffer(bytes))) return;
  const original = bytes.toString("utf8");
  let next = original;
  if (/\.json$/i.test(p)) {
    let data;
    try {
      data = JSON.parse(original);
    } catch {
      return;
    }
    const rows = Array.isArray(data) ? data : data?.messages;
    if (Array.isArray(rows) && rows.some((m) => m && ids.has(m.id))) {
      const result = rechain(rows, data.activeLeafId ?? rows.at(-1)?.id, ids);
      data = Array.isArray(data) ? result.messages : { ...data, ...result };
    }
    next = JSON.stringify(clean.clean(data) ?? null);
  } else if (/\.(ndjson|jsonl)$/i.test(p)) {
    next = original.split("\n").map((line) => {
      if (!line) return "";
      try {
        const r = clean.clean(JSON.parse(line));
        return r === void 0 ? "" : JSON.stringify(r);
      } catch {
        return clean.hasId(line) ? "" : clean.cleanText(line);
      }
    }).filter(Boolean).join("\n") + "\n";
  } else next = clean.cleanMemory(original);
  if (next !== original) write(p, next);
}
export {
  createMessageDeletion,
  purgeDatabase,
  rechain,
  scrubber
};
