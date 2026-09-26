const { chromium } = require(process.env.OMB_PLAYWRIGHT_MODULE || "playwright");
const assert = require("node:assert/strict");
const { mkdirSync, writeFileSync } = require("node:fs");
const { join } = require("node:path");
const [url, evidence] = process.argv.slice(2);
(async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1180, height: 1300 } });
    page.on('pageerror', error => console.error('Renderer error:', error.message));
    page.on('console', message => { if (message.type() === 'error') console.error(message.text()); });
    await page.goto(url);
    const legacy = page.locator('[data-legacy] [data-device-access]');
    const modern = page.locator('[data-server] [data-device-access]');
    try { await legacy.locator('select').waitFor(); await modern.locator('select').waitFor(); }
    catch (error) { console.error(await page.locator('body').innerText()); throw error; }
    assert.equal(await legacy.locator('select').inputValue(), 'client');
    assert.match(await legacy.innerText(), /Gesperrt.*Bot-Verwaltung/);
    await legacy.locator('select').selectOption('admin');
    await legacy.getByRole('status').waitFor();
    assert.equal(await legacy.locator('select').inputValue(), 'admin');
    assert.match(await legacy.innerText(), /Verbindungsrechte gespeichert/);
    assert.equal((await page.request.get(url + '/fixture/rights').then(r => r.json())).legacy, 'admin');
    await modern.locator('select').selectOption('admin');
    await modern.getByRole('status').waitFor();
    assert.equal((await page.request.get(url + '/fixture/rights').then(r => r.json())).server.role, 'admin');
    mkdirSync(evidence, { recursive: true });
    await page.screenshot({ path: join(evidence, 'pairing-full-access.png'), fullPage: true });
    await page.request.get(url + '/fixture/fail');
    await legacy.locator('select').selectOption('client');
    await legacy.getByRole('alert').waitFor();
    assert.match(await legacy.getByRole('alert').innerText(), /Datenträger voll/);
    assert.equal(await legacy.locator('select').inputValue(), 'admin');
    assert.equal(await legacy.getByRole('status').count(), 0, 'failed save must not claim success');
    assert.equal((await page.request.get(url + '/fixture/rights').then(r => r.json())).legacy, 'admin');
    await page.screenshot({ path: join(evidence, 'pairing-save-failed.png'), fullPage: true });
    await page.request.get(url + '/fixture/fail');
    await legacy.locator('select').selectOption('client'); await legacy.getByRole('status').waitFor();
    await modern.locator('select').selectOption('client');
    await page.waitForFunction(() => [...document.querySelectorAll('[data-device-access] select')].every(node => node.value === 'client'));
    const rights = await page.request.get(url + '/fixture/rights').then(r => r.json());
    assert.equal(rights.legacy, 'client'); assert.equal(rights.server.role, 'client');
    await page.reload(); await legacy.locator('select').waitFor(); await modern.locator('select').waitFor();
    assert.equal(await legacy.locator('select').inputValue(), 'client'); assert.equal(await modern.locator('select').inputValue(), 'client');
    assert.doesNotMatch(await page.locator('body').innerText(), /ältere Kopplung|Unknown \(older pairing\)/);
    await page.screenshot({ path: join(evidence, 'pairing-restricted-access.png'), fullPage: true });
    writeFileSync(join(evidence, 'pairing-ui-check.json'), JSON.stringify({ samePairing: true, legacyAndServerChangesVerified: true, persistedAfterReload: true, failureShowsCauseAndKeepsRights: true, rights }, null, 2));
    console.log('Pairing UI verified: both connections, persisted rights and concrete save failure.');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
