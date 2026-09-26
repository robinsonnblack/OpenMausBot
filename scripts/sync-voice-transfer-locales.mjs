import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { parseStrings } from "./generate-android-locale.mjs";

const root = fileURLToPath(new URL("../", import.meta.url));
const directories = { en: "values", de: "values-de", es: "values-es", fr: "values-fr", hi: "values-hi", ja: "values-ja", "pt-br": "values-pt-rBR", zh: "values-zh", "zh-tw": "values-zh-rTW", uk: "values-uk" };
const english = JSON.parse(readFileSync(join(root, "src/locales/en.json"), "utf8"));
const keys = Object.keys(english).filter(key => key.startsWith("transfer.") || key.startsWith("phoneVoice."));
const encode = text => text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/'/g, "\\'").replace(/"/g, '\\"').replace(/\n/g, "\\n");
for (const [code, directory] of Object.entries(directories)) {
  const catalog = JSON.parse(readFileSync(join(root, `src/locales/${code}.json`), "utf8"));
  const path = join(root, "android/app/src/main/res", directory, "strings.xml");
  let xml = readFileSync(path, "utf8");
  const existing = parseStrings(xml);
  for (const key of keys) {
    if (typeof catalog[key] !== "string") throw Error(`Missing translation ${code}:${key}`);
    const name = key.replace("phoneVoice.", "phone_voice_").replaceAll(".", "_").toLowerCase();
    const text = key === "phoneVoice.provider_error" ? catalog[key].replace("{code}", "%1$d") : catalog[key];
    const entry = `    <string name="${name}">${encode(text)}</string>`;
    if (existing.has(name)) xml = xml.replace(new RegExp(`    <string\\s+name="${name}"[^>]*>[\\s\\S]*?</string>`), entry);
    else xml = xml.replace("</resources>", `${entry}\n</resources>`);
  }
  writeFileSync(path, xml);
}
