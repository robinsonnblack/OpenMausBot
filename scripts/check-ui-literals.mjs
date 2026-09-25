// New visible UI copy belongs in translation resources. Existing direct
// literals are tracked as debt so feature work cannot add to that debt.
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const baselinePath = join(root, "scripts", "ui-literal-debt.json");
const visibleAttributes = new Set(["placeholder", "aria-label", "title", "alt", "label"]);

function filesUnder(folder, extension) {
  const files = [];
  function walk(path) {
    for (const entry of readdirSync(path, { withFileTypes: true })) {
      const full = join(path, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith(extension) && !entry.name.includes(".test.")) files.push(full);
    }
  }
  walk(folder);
  return files;
}

function key(file, kind, value) {
  return `${relative(root, file).replaceAll("\\", "/")}\u0000${kind}\u0000${value}`;
}

export function desktopLiterals(file, code) {
  const found = [];
  const tree = ts.createSourceFile(file, code, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  function visit(node) {
    if (ts.isJsxText(node)) {
      const value = node.getFullText(tree).replace(/\s+/g, " ").trim();
      if (/[A-Za-z]/.test(value)) found.push(["text", value]);
    }
    if (ts.isJsxAttribute(node) && node.initializer && ts.isStringLiteral(node.initializer)) {
      const attr = node.name.text;
      const value = node.initializer.text.trim();
      if (visibleAttributes.has(attr) && /[A-Za-z]/.test(value)) found.push([attr, value]);
    }
    ts.forEachChild(node, visit);
  }
  visit(tree);
  return found;
}

export function androidLiterals(code) {
  const found = [];
  // Intentionally narrow: only a literal that is passed directly to Compose
  // Text, or a common visible label parameter. Dynamic expressions still
  // require code review and should use stringResource with format arguments.
  const pattern = /\b(Text|label|placeholder)\s*\(?(?:\s*text\s*=\s*)?"((?:\\.|[^"\\])*)"/g;
  for (const match of code.matchAll(pattern)) {
    const value = match[2].trim();
    if (/[A-Za-z]/.test(value)) found.push([match[1], value]);
  }
  return found;
}

function collect() {
  const found = {};
  const add = (file, kind, value) => {
    const id = key(file, kind, value);
    found[id] = (found[id] ?? 0) + 1;
  };
  for (const file of filesUnder(join(root, "src"), ".tsx")) {
    for (const [kind, value] of desktopLiterals(file, readFileSync(file, "utf8"))) add(file, kind, value);
  }
  for (const file of filesUnder(join(root, "android", "app", "src", "main", "kotlin"), ".kt")) {
    if (!file.replaceAll("\\", "/").includes("/ui/")) continue;
    for (const [kind, value] of androidLiterals(readFileSync(file, "utf8"))) add(file, kind, value);
  }
  return found;
}

export function newLiteralErrors(current, baseline) {
  return [
    ...Object.entries(current).filter(([id, count]) => count > (baseline[id] ?? 0))
      .map(([id, count]) => `New hardcoded UI text (${count - (baseline[id] ?? 0)}): ${id.replaceAll("\u0000", " | ")}`),
    ...Object.entries(baseline).filter(([id, count]) => (current[id] ?? 0) < count)
      .map(([id]) => `Resolved hardcoded UI text; shrink the debt baseline: ${id.replaceAll("\u0000", " | ")}`),
  ];
}

export function checkUiLiterals({ update = false } = {}) {
  const current = collect();
  if (update) {
    writeFileSync(baselinePath, `${JSON.stringify({ version: 1, entries: current }, null, 2)}\n`);
    return;
  }
  const baseline = JSON.parse(readFileSync(baselinePath, "utf8"));
  if (baseline.version !== 1) throw new Error("Unknown UI literal baseline version");
  const errors = newLiteralErrors(current, baseline.entries);
  if (errors.length) throw new Error(errors.join("\n"));
  const debt = Object.values(current).reduce((sum, count) => sum + count, 0);
  console.log(`UI literal check: no new direct text; ${debt} existing occurrences remain`);
}

if (process.argv[1] && fileURLToPath(import.meta.url).toLowerCase() === process.argv[1].toLowerCase()) {
  try { checkUiLiterals({ update: process.argv[2] === "--update-baseline" }); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
