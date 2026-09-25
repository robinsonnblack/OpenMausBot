// One-time migration of simple React UI literals into the locale catalog.
import crypto from "node:crypto";
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join, relative } from "node:path";
import ts from "typescript";

const root = process.cwd();
const sourceFile = join(root, "src", "locales", "en.json");
const source = JSON.parse(readFileSync(sourceFile, "utf8"));
const attrNames = new Set(["aria-label", "placeholder", "title", "alt", "subtitle", "hint", "description", "label", "emptyLabel", "confirmLabel", "cancelLabel"]);
const files = [];
function walk(dir) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) walk(path);
    else if (entry.name.endsWith(".tsx") && !path.includes(`${join("src", "components", "testing")}`)) files.push(path);
  }
}
walk(join(root, "src", "components"));
files.push(join(root, "src", "App.tsx"), join(root, "src", "pair", "PairPage.tsx"));

let total = 0;
for (const path of files) {
  const body = readFileSync(path, "utf8");
  const sf = ts.createSourceFile(path, body, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const changes = [];
  const fileStem = relative(join(root, "src"), path).replace(/\.tsx$/, "")
    .split(/[\\/]/).map((part) => part.replace(/[^a-zA-Z0-9]/g, "")).join(".");
  function keyFor(label) {
    const hash = crypto.createHash("sha256").update(`${fileStem}\0${label}`).digest("hex").slice(0, 8);
    const key = `hardcoded.${fileStem}.${hash}`;
    if (Object.hasOwn(source, key) && source[key] !== label) throw new Error(`hash collision ${key}`);
    source[key] = label;
    return key;
  }
  function valid(label) {
    return /^[A-Za-z]/.test(label) && /[a-z]/.test(label)
      && label.length < 260 && !/[<>\r\n]/.test(label);
  }
  function visit(node) {
    if (ts.isJsxText(node)) {
      const raw = body.slice(node.getStart(sf), node.end);
      const label = raw.replace(/\s+/g, " ").trim();
      const parentTag = node.parent && (ts.isJsxElement(node.parent) ? node.parent.openingElement.tagName.getText(sf) : "");
      if (valid(label) && parentTag !== "pre" && parentTag !== "code") {
        const before = raw.match(/^\s*/)?.[0] ?? "";
        const after = raw.match(/\s*$/)?.[0] ?? "";
        changes.push({ start: node.getStart(sf), end: node.end,
          text: `${before}{t(${JSON.stringify(keyFor(label))})}${after}` });
      }
    } else if (ts.isJsxAttribute(node) && attrNames.has(node.name.getText(sf))
      && node.initializer && ts.isStringLiteral(node.initializer)) {
      const label = node.initializer.text.trim();
      if (valid(label)) changes.push({ start: node.initializer.getStart(sf), end: node.initializer.end,
        text: `{t(${JSON.stringify(keyFor(label))})}` });
    }
    ts.forEachChild(node, visit);
  }
  visit(sf);
  if (!changes.length) continue;
  let updated = body;
  for (const change of changes.sort((a, b) => b.start - a.start)) {
    updated = updated.slice(0, change.start) + change.text + updated.slice(change.end);
  }
  if (!/import\s*\{[^}]*\bt\b[^}]*\}\s*from\s*["']@\/lib\/i18n["']/.test(updated)) {
    const firstImport = updated.indexOf("import ");
    updated = updated.slice(0, firstImport) + 'import { t } from "@/lib/i18n";\n' + updated.slice(firstImport);
  }
  writeFileSync(path, updated);
  total += changes.length;
  console.log(`${relative(root, path)}: ${changes.length}`);
}
writeFileSync(sourceFile, `${JSON.stringify(source, null, 2)}\n`);
console.log(`Migrated ${total} direct JSX strings.`);
