import { spawn } from "node:child_process";
import { createServer } from "node:http";
import { mkdtempSync, readFileSync, readdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";
import { launchVerificationServer } from "./control-omb.ts";

const root = fileURLToPath(new URL("..", import.meta.url));
const fixture = await launchVerificationServer();
const companionHome = mkdtempSync(join(tmpdir(), "omb-pairing-ui-"));
process.env.OMB_COMPANION_DIR = companionHome;
const { DeviceRegistry } = await import("../companion/src/devices.ts");
const { createControlServer } = await import("../companion/src/control.ts");
const devices = new DeviceRegistry();
const paired = devices.redeem(devices.openPairing().code, "Android-Testhandy");
if ("error" in paired) throw new Error(paired.error);
const control = createControlServer({ devices, companionPort: 8810, discovery: () => ({ advertising: false, name: "Fixture" }) });
const server = createServer();
let failWrites = false;
try {
  await new Promise<void>(resolve => control.listen(0, "127.0.0.1", resolve));
  const controlUrl = "http://127.0.0.1:" + (control.address() as { port: number }).port;
  const offer = await fetch(fixture.info.url + "/api/auth/pairing", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ label: "Server-Testhandy", scopes: ["client"] }) }).then(r => r.json()) as any;
  const accepted = await fetch(fixture.info.url + "/api/auth/pair", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ code: offer.code, label: "Server-Testhandy" }) }).then(r => r.json()) as any;
  const bundle = await build({ entryPoints: [join(root, "scripts/testing/pairing-access-preview.tsx")], bundle: true, write: false, jsx: "automatic", format: "iife", alias: { "@": join(root, "src") }, loader: { ".css": "empty" }, define: { "process.env.NODE_ENV": '"production"' } });
  const css = readdirSync(join(root, "dist/assets")).find(name => /^index-.*\.css$/.test(name));
  if (!css) throw new Error("Build production CSS first");
  server.on("request", (req, res) => { void (async () => {
    if (req.url === "/fixture.js") { res.setHeader("content-type", "text/javascript"); res.end(bundle.outputFiles[0]!.text); return; }
    if (req.url === "/fixture.css") { res.setHeader("content-type", "text/css"); res.end(readFileSync(join(root, "dist/assets", css))); return; }
    if (req.url === "/fixture/fail") { failWrites = !failWrites; res.setHeader("content-type", "application/json"); res.end(JSON.stringify({ failWrites })); return; }
    if (req.url === "/fixture/rights") { res.setHeader("content-type", "application/json"); res.end(JSON.stringify({ legacy: devices.authenticate(paired.token)?.access, server: await fetch(fixture.info.url + "/api/companion/access", { headers: { authorization: "Bearer " + accepted.token } }).then(r => r.json()) })); return; }
    const path = req.url || "/";
    if (path.startsWith("/api/") || path.startsWith("/fixture/access/") || path === "/fixture/state") {
      const mutation = path.startsWith("/fixture/access/");
      if (mutation && failWrites) { res.statusCode = 500; res.setHeader("content-type", "application/json"); res.end(JSON.stringify({ error: "Speichern fehlgeschlagen: Datenträger voll. Die bisherigen Rechte bleiben aktiv." })); return; }
      const chunks: Buffer[] = []; for await (const chunk of req) chunks.push(chunk);
      const target = mutation ? controlUrl + "/devices/" + path.slice("/fixture/access/".length) + "/access" : path === "/fixture/state" ? controlUrl + "/state" : fixture.info.url + path;
      const response = await fetch(target, { method: req.method, headers: { "content-type": "application/json" }, ...(chunks.length ? { body: Buffer.concat(chunks) } : {}) });
      const body = await response.json();
      res.statusCode = response.status; res.setHeader("content-type", "application/json");
      res.end(JSON.stringify(path.startsWith("/fixture/") ? { enabled: true, keepAwake: false, ...body as object } : body)); return;
    }
    res.setHeader("content-type", "text/html"); res.end('<!doctype html><html><head><meta charset="utf-8"><title>Verbindungsrechte – isolierter Test</title><link rel="stylesheet" href="/fixture.css"></head><body><div id="root"></div><script src="/fixture.js"></script></body></html>');
  })().catch(error => { res.statusCode = 500; res.end(JSON.stringify({ error: error.message })); }); });
  await new Promise<void>(resolve => server.listen(0, "127.0.0.1", resolve));
  const url = "http://127.0.0.1:" + (server.address() as { port: number }).port;
  const child = spawn(process.execPath, [join(root, "scripts/testing/pairing-access-ui-smoke.cjs"), url, process.env.OMB_PAIRING_EVIDENCE || join(root, ".omb-scratch/pairing-access")], { stdio: "inherit", env: process.env, windowsHide: true });
  const code = await new Promise<number>((resolve, reject) => { child.once("error", reject); child.once("exit", code => resolve(code ?? 1)); });
  if (code !== 0) throw new Error("Pairing UI verification failed");
  console.log(JSON.stringify({ ...fixture.info, pairingUiVerified: true }));
} finally {
  server.closeAllConnections(); await new Promise<void>(resolve => server.close(() => resolve()));
  control.closeAllConnections(); await new Promise<void>(resolve => control.close(() => resolve()));
  await fixture.close(); rmSync(companionHome, { recursive: true, force: true });
}
