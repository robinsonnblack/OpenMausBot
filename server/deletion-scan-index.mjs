import fs from "node:fs";
import { isMainThread, parentPort, workerData } from "node:worker_threads";
function hash8(bytes, offset) {
  let h = 2166136261;
  for (let i = 0; i < 8; i++) h = Math.imul(h ^ bytes[offset + i], 16777619);
  return h >>> 0;
}
function second(h) {
  h ^= h >>> 16;
  h = Math.imul(h, 2246822507);
  return (h ^ h >>> 13) >>> 0;
}
function makeIndex(bytes) {
  let length = 1024;
  while (length < Math.min(1048576, Math.ceil(bytes.length / 4))) length *= 2;
  const bits = new Uint8Array(length), mask = length * 8 - 1;
  for (let i = 0; i + 8 <= bytes.length; i += 8) {
    const value = hash8(bytes, i), h = value & mask, j = second(value) & mask;
    bits[h >>> 3] |= 1 << (h & 7);
    bits[j >>> 3] |= 1 << (j & 7);
  }
  return { bits, mask };
}
function mayContain(index, needle) {
  if (needle.length < 15) return true;
  const { bits, mask } = index;
  for (let alignment = 0; alignment < 8; alignment++) {
    let possible = true;
    for (let i = alignment; i + 8 <= needle.length; i += 8) {
      const hash = hash8(needle, i), h = hash & mask, j = second(hash) & mask;
      if (!(bits[h >>> 3] & 1 << (h & 7)) || !(bits[j >>> 3] & 1 << (j & 7))) {
        possible = false;
        break;
      }
    }
    if (possible) return true;
  }
  return false;
}
function nativeThreadIds(bytes) {
  const ids = /* @__PURE__ */ new Set();
  let pos = -1;
  while ((pos = bytes.indexOf("thread/started", pos + 1)) >= 0) {
    const start = bytes.lastIndexOf(10, pos) + 1, end = bytes.indexOf(10, pos);
    try {
      const m = JSON.parse(bytes.subarray(start, end < 0 ? bytes.length : end).toString("utf8")).msg;
      if (m?.method === "thread/started" && m.params?.thread?.id) ids.add(m.params.thread.id);
    } catch {
    }
  }
  return [...ids];
}
if (!isMainThread) {
  for (const path of workerData.files) {
    try {
      const before = fs.statSync(path), bytes = fs.readFileSync(path), after = fs.statSync(path);
      if (before.size !== after.size || before.mtimeMs !== after.mtimeMs) continue;
      const index = makeIndex(bytes);
      parentPort.postMessage({ path, size: after.size, mtime: after.mtimeMs, ...index, nativeIds: nativeThreadIds(bytes) }, [index.bits.buffer]);
    } catch {
    }
  }
}
export {
  makeIndex,
  mayContain,
  nativeThreadIds
};
