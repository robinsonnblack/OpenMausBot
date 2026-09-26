import { createServer } from "node:http";
import { readFileSync, readdirSync, mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { build } from "esbuild";
import { launchVerificationServer } from "./control-omb.ts";
const root = fileURLToPath(new URL("..", import.meta.url));
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.OMB_PLAYWRIGHT_MODULE || "playwright");
const fixture = await launchVerificationServer();
const server = createServer();
let failWrites = false;
let browser: any;
try {
  const bundle = await build({ entryPoints: [join(root, "scripts/testing/image-limits-preview.tsx")], bundle: true, write: false, jsx: "automatic", format: "iife", alias: { "@": join(root, "src") }, define: { "process.env.NODE_ENV": '"production"' } });
  const css = readdirSync(join(root, "dist/assets")).find(name => /^index-.*\.css$/.test(name))!;
  server.on("request", (req, res) => { void (async () => {
    if (req.url === "/fixture.js") { res.setHeader("content-type", "text/javascript"); res.end(bundle.outputFiles[0]!.text); return; }
    if (req.url === "/fixture.css") { res.setHeader("content-type", "text/css"); res.end(readFileSync(join(root, "dist/assets", css))); return; }
    if (req.url?.startsWith("/api/")) {
      if (failWrites && req.method === "PATCH") { res.statusCode = 500; res.setHeader("content-type", "application/json"); res.end(JSON.stringify({ error: "Datenträger voll" })); return; }
      const chunks: Buffer[] = []; for await (const chunk of req) chunks.push(chunk);
      const response = await fetch(fixture.info.url + req.url, { method: req.method, headers: { "content-type": "application/json" }, ...(chunks.length ? { body: Buffer.concat(chunks) } : {}) });
      res.statusCode = response.status; res.setHeader("content-type", response.headers.get("content-type") || "application/json");
      // This fixture does not need a persistent event connection.
      if (req.url === "/api/events") { res.end(); return; }
      res.end(Buffer.from(await response.arrayBuffer())); return;
    }
    res.setHeader("content-type", "text/html"); res.end('<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/fixture.css"></head><body><div id="root"></div><script src="/fixture.js"></script></body></html>');
  })().catch(error => { res.statusCode = 500; res.end(JSON.stringify({ error: error.message })); }); });
  await new Promise<void>(done => server.listen(0, "127.0.0.1", done));
  const url = `http://127.0.0.1:${(server.address() as { port: number }).port}`;
  browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 500, height: 700 } });
  await page.goto(url);
  const count = page.getByLabel("Maximale Bildanzahl", { exact: true });
  const size = page.getByLabel("Gesamtgröße der Bilder (MB)", { exact: true });
  await count.waitFor();
  if (await count.inputValue() !== "30" || await size.inputValue() !== "60") throw new Error("Incorrect defaults");
  if (await count.getAttribute("max") || await size.getAttribute("max")) throw new Error("Unexpected upper ceiling");
  if (await page.getByText("Diese Grenzen", { exact: false }).count()) throw new Error("Explanation shown without help");
  await page.getByRole("button", { name: "Hilfe zu Bildgrenzen" }).click();
  await page.getByRole("dialog").getByText("Diese Grenzen", { exact: false }).waitFor();
  await page.getByRole("button", { name: "Schließen", exact: true }).click();
  await count.fill("100000"); await size.fill("1000000");
  await page.getByRole("button", { name: "Grenzen speichern", exact: true }).click();
  await page.getByRole("button", { name: "✓ Grenzen gespeichert", exact: true }).waitFor();
  const confirmed = await fetch(fixture.info.url + "/api/config").then(r => r.json()) as any;
  if (confirmed.imageAttachments.maxImages !== 100000 || confirmed.imageAttachments.maxTotalImageBytes !== 1_000_000_000_000) throw new Error("Save did not reach server");
  await page.reload(); await count.waitFor(); await page.waitForFunction(() => document.querySelector<HTMLInputElement>('input[inputmode="numeric"]')?.value === "100000");
  await count.fill("100001"); failWrites = true;
  await page.getByRole("button", { name: "Grenzen speichern", exact: true }).click();
  await page.getByRole("alert").getByText("Datenträger voll").waitFor();
  if (await page.getByRole("button", { name: "✓ Grenzen gespeichert", exact: true }).count()) throw new Error("Failure claims success");
  const evidence = process.env.OMB_IMAGE_LIMIT_EVIDENCE || join(root, ".omb-scratch/image-limits"); mkdirSync(evidence, { recursive: true });
  await page.screenshot({ path: join(evidence, "settings-failure.png") });
  const result = { defaultsVerified: true, higherValuesPersist: true, noUpperCeiling: true, helpOnlyOnRequest: true, failureFeedbackVerified: true };
  writeFileSync(join(evidence, "verification.json"), JSON.stringify(result, null, 2)); console.log(JSON.stringify(result));
} finally {
  await browser?.close(); server.closeAllConnections(); await new Promise<void>(done => server.close(() => done())); await fixture.close();
}
