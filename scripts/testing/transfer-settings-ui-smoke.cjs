const { chromium } = require(process.env.OMB_PLAYWRIGHT_MODULE || "playwright");
const assert = require("node:assert/strict");
const { mkdirSync } = require("node:fs");
const [previewUrl, apiUrl, evidence] = process.argv.slice(2);
if (!previewUrl || !apiUrl || !evidence) throw Error("Pass the isolated preview URL, isolated API URL, and evidence folder");
(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1450, height: 1100 } });
  async function api(path, method = "GET", body) {
    const response = await fetch(apiUrl + path, { method, headers: { "content-type": "application/json" }, ...(body ? { body: JSON.stringify(body) } : {}) });
    assert(response.ok, await response.clone().text()); return response.json();
  }
  try {
    const bots = (await api("/api/bots?messages=0")).bots;
    const source = bots.find(bot => bot.name === "Settings Atlas"), target = bots.find(bot => bot.name === "Settings Juniper");
    await api(`/api/bots/${source.id}`, "PATCH", { voice: "fixture-voice", speakReplies: true });
    await api(`/api/bots/${target.id}`, "PATCH", { description: "Keep this target description" });
    await page.goto(previewUrl, { waitUntil: "domcontentloaded", timeout: 120000 });
    await page.getByRole("button", { name: /Settings Atlas/ }).click();
    await page.getByRole("button", { name: "Open settings", exact: true }).click();
    await page.getByRole("button", { name: "Transfer settings to other bots", exact: true }).click();
    await page.getByRole("checkbox", { name: "Settings Juniper", exact: true }).check();
    await page.getByRole("checkbox", { name: "Voice", exact: true }).check();
    await page.getByRole("checkbox", { name: "Read replies aloud", exact: true }).check();
    await page.getByRole("button", { name: "Review transfer", exact: true }).click();
    await page.getByRole("button", { name: "Apply selected settings", exact: true }).waitFor();
    assert((await page.locator("body").innerText()).includes("fixture-voice"));
    assert.equal(await page.evaluate(() => document.activeElement.textContent.trim()), "Cancel");
    mkdirSync(evidence, { recursive: true });
    await page.screenshot({ path: `${evidence}/review.png` });
    await page.getByRole("button", { name: "Apply selected settings", exact: true }).click();
    await page.getByRole("status").filter({ hasText: "Settings applied to 1 bots." }).waitFor();
    const result = (await api(`/api/bots/${target.id}/transfer-settings`)).settings;
    assert.equal(result.voice, "fixture-voice"); assert.equal(result.speakReplies, true);
    assert.equal(result.description, "Keep this target description");
    await api(`/api/bots/${source.id}`, "PATCH", { voice: "later-source-change" });
    assert.equal((await api(`/api/bots/${target.id}/transfer-settings`)).settings.voice, "fixture-voice");
    await page.screenshot({ path: `${evidence}/completed.png` });
    console.log("Settings review, explicit confirmation, selective copy and later independence passed.");
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
