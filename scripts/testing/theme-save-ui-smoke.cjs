const { chromium } = require(process.env.OMB_PLAYWRIGHT_MODULE || "playwright");
const assert = require("node:assert/strict");
const { mkdirSync, writeFileSync } = require("node:fs");
const { join } = require("node:path");
const [url, _profile, evidence] = process.argv.slice(2);
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function until(fn) {
  const deadline = Date.now() + 20000;
  while (Date.now() < deadline) { const value = await fn(); if (value) return value; await delay(50); }
  throw new Error("Theme UI state timed out");
}
(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1280, height: 1100 } });
  const page = await context.newPage();
  const win = {
    loadURL: (target) => page.goto(target),
    webContents: {
      executeJavaScript: (source) => page.evaluate(source),
      capturePage: async () => { const png = await page.screenshot(); return { toPNG: () => png }; },
    },
  };
  const evaluate = (source) => win.webContents.executeJavaScript(source);
  const state = () => evaluate(`(() => {
    const button = document.querySelector('.custom-theme-save');
    return button && { text: button.textContent.trim(), saved: button.dataset.saved,
      check: !!button.querySelector('svg'), disabled: button.disabled,
      skin: document.documentElement.dataset.skin,
      panel: getComputedStyle(document.querySelector('[data-fixture-sidebar]')).backgroundColor,
      stored: JSON.parse(localStorage.getItem('omb-custom-theme')),
      alert: document.querySelector('[role="alert"]')?.textContent ?? null,
      animation: getComputedStyle(button.querySelector('.custom-theme-save-copy')).animationName };
  })()`);
  const edit = (selector, value) => evaluate(`(() => {
    const input = document.querySelector(${JSON.stringify(selector)});
    const proto = input.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(input, ${JSON.stringify(value)});
    input.dispatchEvent(new Event(input.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }));
  })()`);
  const save = () => evaluate(`document.querySelector('.custom-theme-save').click()`);
  mkdirSync(evidence, { recursive: true });
  try {
    await win.loadURL(url);
    await until(() => evaluate(`!![...document.querySelectorAll('button')].find(b => b.textContent.includes('Edit custom theme'))`));
    const choosePreset = (name) => page.getByRole('button').filter({ has: page.getByText(name, { exact: true }) }).click();
    await choosePreset('Cyan GPT');
    const cyan = await evaluate(`({skin: document.documentElement.dataset.skin, layout: document.documentElement.dataset.chatLayout, bubble: document.documentElement.dataset.invertedUserBubble, panel: getComputedStyle(document.querySelector('[data-fixture-sidebar]')).backgroundColor, custom: localStorage.getItem('omb-custom-theme')})`);
    assert.equal(cyan.skin, 'cyan-gpt');
    assert.equal(cyan.layout, 'chatgpt');
    assert.equal(cyan.bubble, 'true');
    assert.equal(cyan.panel, 'rgb(231, 248, 249)');
    assert.equal(cyan.custom, null, 'choosing Cyan GPT must not overwrite the custom palette');
    await win.loadURL(url);
    await until(() => evaluate(`document.documentElement.dataset.skin === 'cyan-gpt'`));
    await choosePreset('ChatGPT');
    await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent.includes('Edit custom theme')).click()`);
    const initial = await until(state);
    assert.equal(initial.panel, "rgb(253, 250, 247)");
    assert.equal(initial.disabled, false);
    assert.equal(initial.text, "Eigenes Design speichern und verwenden");
    for (const width of [1280, 900, 560, 360]) {
      await page.setViewportSize({ width, height: 1100 });
      const overlap = await page.evaluate(() => [...document.querySelectorAll('input[type="color"]')].map(input => {
        const label = input.closest('label'); const text = label.querySelector('span');
        const a = text.getBoundingClientRect(), b = input.getBoundingClientRect();
        return { text: text.textContent, overlap: a.bottom > b.top + 1, clipped: text.scrollWidth > text.clientWidth + 1 };
      }).filter(row => row.overlap || row.clipped));
      assert.deepEqual(overlap, [], 'All color names must remain readable at width ' + width);
      await page.locator('[aria-label="accent-ink color"]').scrollIntoViewIfNeeded();
      await page.screenshot({ path: join(evidence, 'theme-colors-' + width + '.png') });
    }
    await page.setViewportSize({ width: 1280, height: 1100 });
    await save();
    const saved = await until(async () => { const s = await state(); return s?.saved === 'true' && s; });
    assert.equal(saved.text, "Eigenes Design gespeichert");
    assert.equal(saved.check, true);
    assert.equal(saved.skin, "custom");
    assert.equal(saved.stored.panel, "#fdfaf7");
    assert.equal(saved.animation, "custom-theme-saved");
    await delay(900);
    assert.equal((await state()).saved, "true");
    writeFileSync(join(evidence, "theme-saved.png"), (await win.webContents.capturePage()).toPNG());
    await edit('[aria-label="panel hex"]', '#faf7f4');
    const edited = await until(async () => { const s = await state(); return s?.saved === 'false' && s; });
    assert.equal(edited.text, "Eigenes Design speichern und verwenden");
    assert.equal(edited.check, false);
    assert.equal(edited.stored.panel, "#fdfaf7");
    await save();
    await until(async () => (await state())?.stored?.panel === '#faf7f4');
    assert.equal((await state()).panel, "rgb(250, 247, 244)");
    await win.loadURL(url);
    await until(() => evaluate(`!![...document.querySelectorAll('button')].find(b => b.textContent.includes('Edit custom theme'))`));
    await evaluate(`[...document.querySelectorAll('button')].find(b => b.textContent.includes('Edit custom theme')).click()`);
    assert.equal((await until(state)).saved, "true", "saved feedback survives reopening/reload");
    await edit('select:nth-of-type(1)', 'daylight');
    await until(async () => (await state())?.saved === 'false');
    await save();
    await until(async () => (await state())?.saved === 'true');
    // Query the layout selector explicitly: each select lives in a separate label.
    await evaluate(`(() => { const input = document.querySelectorAll('select')[1]; Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set.call(input, 'chatgpt'); input.dispatchEvent(new Event('change', { bubbles: true })); })()`);
    await until(async () => (await state())?.saved === 'false');
    await evaluate(`window.fixtureSetItem = Storage.prototype.setItem; Storage.prototype.setItem = function() { throw new Error('fixture storage failure'); }`);
    await save();
    await until(async () => !!(await state())?.alert);
    assert.equal((await state()).saved, "false", "failed persistence must not claim success");
    await evaluate(`Storage.prototype.setItem = window.fixtureSetItem`);
    await save();
    await until(async () => (await state())?.saved === 'true');
    await edit('[aria-label="panel hex"]', 'invalid');
    await until(async () => (await state())?.disabled === true);
    await edit('[aria-label="panel hex"]', '#fdfaf7');
    await save();
    await until(async () => (await state())?.saved === 'true');
    await edit('select:nth-of-type(1)', 'cyan-gpt');
    await until(async () => (await state())?.saved === 'false');
    await save();
    await until(async () => (await state())?.stored?.panel === '#e7f8f9');
    assert.equal((await state()).stored.layout, 'chatgpt');
    await choosePreset('Cyan GPT');
    await win.loadURL(url);
    await until(() => evaluate(`document.documentElement.dataset.skin === 'cyan-gpt'`));
    writeFileSync(join(evidence, "cyan-gpt.png"), await page.screenshot());
    writeFileSync(join(evidence, "workflow.json"), JSON.stringify({ initial, saved, edited, result: "passed", checks: ["reference palette", "save animation and check", "persistent success", "edit resets", "reload persistence", "preset/layout reset", "storage failure", "invalid input"] }, null, 2));
    console.log("Theme save UI workflow passed: " + evidence);
    await browser.close();
  } catch (error) {
    console.error(error);
    writeFileSync(join(evidence, "failure.png"), (await win.webContents.capturePage()).toPNG());
    await browser.close();
    process.exitCode = 1;
  }
})().catch((error) => { console.error(error); process.exitCode = 1; });
