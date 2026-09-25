import { test } from "vitest";
import assert from "node:assert/strict";
import { parseStrings, placeholders, usageIndex, validateLocaleCoverage } from "./generate-android-locale.mjs";

test("parses escaped Android strings without changing resource names", () => {
  const strings = parseStrings(`<resources><string name="pair_hint" formatted="false">Bob\\'s &amp; Alice\\'s PC</string></resources>`);
  assert.equal(strings.get("pair_hint").text, "Bob's & Alice's PC");
  assert.match(strings.get("pair_hint").attributes, /formatted="false"/);
});

test("finds screen usage for ambiguous labels", () => {
  const sites = usageIndex([{ path: "ui/MemorySheet.kt", text:
    "Text(stringResource(R.string.ui_open))\nText(stringResource(R.string.ui_delete))" }]);
  assert.equal(sites.get("ui_open")[0].screen, "MemorySheet.kt");
  assert.equal(sites.get("ui_delete")[0].line, 2);
});

test("tracks Android format arguments and named placeholders", () => {
  assert.deepEqual(placeholders("Delete %1$s from {name} in %2$d days"), ["%1$s", "%2$d", "{name}"]);
});

test("requires every translatable resource and preserves its format arguments", () => {
  const english = parseStrings('<resources><string name="app_name">OpenMausBot</string><string name="title">Memory</string><string name="count">%1$s files</string></resources>');
  const partial = parseStrings('<resources><string name="title">Erinnerung</string><string name="count">Dateien</string></resources>');
  assert.deepEqual(validateLocaleCoverage(english, partial), ["count: format arguments differ from English"]);
  partial.set("count", { text: "%1$s Dateien" });
  assert.deepEqual(validateLocaleCoverage(english, partial), []);
  partial.delete("title");
  assert.deepEqual(validateLocaleCoverage(english, partial), ["title: missing translation"]);
});
