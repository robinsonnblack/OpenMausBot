import assert from "node:assert/strict";
import { test } from "node:test";

import { main, promptFor, validateCorrections } from "./review-android-locale.mjs";

const source = new Map([["pairing", { text: "Connect %1$s to this computer" }]]);
const translated = new Map([["pairing", { text: "Dieses Gerät mit %1$s verbinden" }]]);

test("accepts a review with no corrections", () => {
  assert.doesNotThrow(() => validateCorrections([], new Set(["pairing"]), source, translated));
});

test("requires selecting one reviewed proposal before applying it", () => {
  assert.throws(() => main(["de", "German", "--apply"]), /apply-key/);
});

test("provides existing translation of related text for ambiguous terms", () => {
  const english = new Map([
    ["ui_memory_save", { text: "Save memory" }],
    ["ui_other", { text: "Other" }],
    ["ui_memory_open", { text: "Open memory" }],
  ]);
  const german = new Map([
    ["ui_memory_save", { text: "Erinnerung speichern" }],
    ["ui_memory_open", { text: "Erinnerung öffnen" }],
  ]);
  const index = new Map([
    ["ui_memory_save", [{ screen: "BotMemorySection.kt" }]],
    ["ui_other", [{ screen: "BotMemorySection.kt" }]],
    ["ui_memory_open", [{ screen: "BotMemorySection.kt" }]],
  ]);
  const prompt = promptFor(["ui_memory_save"], "BotMemorySection.kt", english, german, index, "German", "de");
  assert.ok(prompt.indexOf('"ui_memory_open"') < prompt.indexOf('"ui_other"'));
  assert.match(prompt, /Erinnerung öffnen/);
  assert.match(prompt, /'Erinnerung' in the singular/);
});

test("accepts a contextual correction that preserves Android format arguments", () => {
  assert.doesNotThrow(() => validateCorrections([
    { key: "pairing", replacement: "%1$s mit diesem Computer verbinden", reason: "The target is the computer." },
  ], new Set(["pairing"]), source, translated));
});

test("rejects corrections that lose a placeholder or target an unrequested key", () => {
  assert.throws(() => validateCorrections([
    { key: "pairing", replacement: "Mit diesem Computer verbinden", reason: "Shorter" },
  ], new Set(["pairing"]), source, translated), /placeholder/);
  assert.throws(() => validateCorrections([
    { key: "other", replacement: "Anders", reason: "Different" },
  ], new Set(["pairing"]), source, translated), /unknown/);
});
