import { app, BrowserWindow } from "electron";
import { mkdtemp, mkdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import assert from "node:assert/strict";
async function main() {
const data = await mkdtemp(path.join(os.tmpdir(), "omb-inspector-preference-"));
app.setPath("userData", data);
app.disableHardwareAcceleration();
const output = process.env.OMB_VERIFY_OUTPUT;
if (!output) throw new Error("Set OMB_VERIFY_OUTPUT");
let win, preview;
const report = { passed: false, checks: [] };
try {
  await app.whenReady();
  const { mountPreview } = await import("./preview-fixture.ts");
  preview = await mountPreview({ info: { url: "http://127.0.0.1:1" } }, {
    entry: "/scripts/testing/prompt-inspector-preference-preview.tsx", route: "/__inspector.html", title: "Inspector fixture", logLevel: "silent",
  });
  win = new BrowserWindow({ show: false, width: 800, height: 600 });
  await win.loadURL(preview.previewUrl);
  const js = code => win.webContents.executeJavaScript(code);
  const until = async code => {
    const end = Date.now() + 15000;
    while (Date.now() < end) { if (await js(code)) return; await new Promise(resolve => setTimeout(resolve, 80)); }
    throw new Error("Timed out: " + code);
  };
  await until("!!document.querySelector('[role=switch]')");
  assert.equal(await js("document.querySelector('header button') === null"), true);
  await js("document.querySelector('[role=switch]').click()");
  await until("document.querySelector('[role=switch]').getAttribute('aria-checked') === 'true' && !!document.querySelector('header button svg.lucide-braces')");
  report.checks.push("Hidden by default; setting gives immediate switch feedback and reveals the braces icon");
  await win.reload();
  await until("!!document.querySelector('header button')");
  await js("document.querySelector('header button').click()");
  await until("!!document.querySelector('dialog[open]')");
  await js("document.querySelector('[role=switch]').click()");
  await until("document.querySelector('header button') === null && document.querySelector('dialog') === null");
  await win.reload();
  await until("!!document.querySelector('[role=switch]')");
  assert.equal(await js("document.querySelector('header button') === null"), true);
  report.checks.push("Enabled/disabled choice survives reload; disabling also closes an open inspector");
  report.passed = true;
} catch (error) { report.error = String(error.stack || error); }
finally {
  await mkdir(output, { recursive: true });
  await writeFile(path.join(output, "report.json"), JSON.stringify(report, null, 2));
  win?.destroy(); await preview?.close(); app.exit(report.passed ? 0 : 1);
}

}
void main();
