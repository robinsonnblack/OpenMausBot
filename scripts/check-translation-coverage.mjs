// Ratchet legacy translation gaps while requiring every new UI string in
// every shipped language. The baseline is deliberately updated by a separate
// command; CI never accepts new omissions automatically.
import { createHash } from "node:crypto";
import { readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const localesDir = join(root, "src", "locales");
const resDir = join(root, "android", "app", "src", "main", "res");
const baselinePath = join(root, "scripts", "translation-debt.json");

const hash = (value) => createHash("sha256").update(value).digest("hex");
const json = (path) => JSON.parse(readFileSync(path, "utf8"));

export function androidStrings(xml) {
  const strings = {};
  const pattern = /<string\s+([^>]*?)>([\s\S]*?)<\/string>/g;
  for (const match of xml.matchAll(pattern)) {
    const name = /\bname="([^"]+)"/.exec(match[1])?.[1];
    if (!name) throw new Error("Android string without a name");
    if (Object.hasOwn(strings, name)) throw new Error(`Duplicate Android string: ${name}`);
    // Android's product name does not need translation.
    if (name !== "app_name" && !/\btranslatable="false"/.test(match[1])) strings[name] = match[2];
  }
  return strings;
}

export function validateCoverage(source, packs, debt, label) {
  const errors = [];
  const sourceKeys = Object.keys(source);
  for (const [locale, pack] of Object.entries(packs)) {
    const exceptions = debt?.[locale] ?? {};
    for (const key of sourceKeys) {
      const hasTranslation = Object.hasOwn(pack, key) && String(pack[key]).trim() !== "";
      const accepted = exceptions[key] === hash(String(source[key]));
      if (!hasTranslation && !accepted) errors.push(`${label}/${locale}: ${key} needs a translation`);
      if (hasTranslation && Object.hasOwn(exceptions, key)) {
        errors.push(`${label}/${locale}: ${key} is translated; remove its debt exception`);
      }
    }
    for (const key of Object.keys(exceptions)) {
      if (!Object.hasOwn(source, key)) errors.push(`${label}/${locale}: obsolete debt exception ${key}`);
    }
  }
  for (const locale of Object.keys(debt ?? {})) {
    if (!Object.hasOwn(packs, locale)) errors.push(`${label}/${locale}: debt locale is no longer shipped`);
  }
  return errors;
}

function missingDebt(source, packs) {
  return Object.fromEntries(Object.entries(packs).map(([locale, pack]) => [
    locale,
    Object.fromEntries(Object.entries(source)
      .filter(([key]) => !Object.hasOwn(pack, key) || String(pack[key]).trim() === "")
      .map(([key, value]) => [key, hash(String(value))])),
  ]));
}

export function checkTranslationCoverage({ update = false } = {}) {
  const source = json(join(localesDir, "en.json"));
  const localeFiles = readdirSync(localesDir).filter((name) =>
    name.endsWith(".json") && !["en.json", "source-hashes.json"].includes(name));
  const packs = Object.fromEntries(localeFiles.map((name) => [name.slice(0, -5), json(join(localesDir, name))]));
  const androidSource = androidStrings(readFileSync(join(resDir, "values", "strings.xml"), "utf8"));
  const androidPacks = Object.fromEntries(Object.keys(packs).map((locale) => {
    const androidCode = locale.replace(/-([a-z]+)/g, (_, region) => `-r${region.toUpperCase()}`);
    const path = join(resDir, `values-${androidCode}`, "strings.xml");
    let contents;
    try { contents = readFileSync(path, "utf8"); } catch (error) {
      if (error?.code !== "ENOENT") throw error;
    }
    return [locale, contents ? androidStrings(contents) : {}];
  }));
  if (update) {
    const debt = { version: 1, desktop: missingDebt(source, packs), android: missingDebt(androidSource, androidPacks) };
    writeFileSync(baselinePath, `${JSON.stringify(debt, null, 2)}\n`);
    return;
  }
  const debt = json(baselinePath);
  if (debt.version !== 1) throw new Error("Unknown translation debt baseline version");
  const errors = [
    ...validateCoverage(source, packs, debt.desktop, "desktop"),
    ...validateCoverage(androidSource, androidPacks, debt.android, "android"),
  ];
  if (errors.length) throw new Error(errors.join("\n"));
  const count = (section) => Object.values(section).reduce((sum, entries) => sum + Object.keys(entries).length, 0);
  console.log(`Translation coverage: no new gaps; ${count(debt.desktop)} desktop and ${count(debt.android)} Android legacy gaps remain`);
}

if (process.argv[1] && fileURLToPath(import.meta.url).toLowerCase() === process.argv[1].toLowerCase()) {
  try { checkTranslationCoverage({ update: process.argv[2] === "--update-baseline" }); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
