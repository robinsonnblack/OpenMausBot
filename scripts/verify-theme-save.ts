// Exercise the real picker in an isolated browser context and fake-engine home.
import { spawn } from "node:child_process";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createServer } from "node:http";
import { readFileSync, readdirSync } from "node:fs";
import { build } from "esbuild";
import { launchVerificationServer } from "./control-omb.ts";

const root = fileURLToPath(new URL("..", import.meta.url));
const fixture = await launchVerificationServer();
const server = createServer();
try {
  const bundle = await build({
    entryPoints: [join(root, "scripts/testing/theme-save-preview.tsx")],
    bundle: true, write: false, minify: true, jsx: "automatic", format: "iife",
    alias: { "@": join(root, "src") }, loader: { ".css": "empty" },
    define: { "process.env.NODE_ENV": '"production"' },
  });
  const css = readdirSync(join(root, "dist/assets")).find((name) => /^index-.*\.css$/.test(name));
  if (!css) throw new Error("Build the production CSS before running this fixture");
  server.on("request", (req, res) => {
    if (req.url === "/fixture.js") {
      res.setHeader("content-type", "text/javascript"); res.end(bundle.outputFiles[0]!.text); return;
    }
    if (req.url?.startsWith("/assets/")) {
      const path = resolve(root, "dist", `.${req.url}`);
      if (!path.startsWith(join(root, "dist/assets"))) { res.statusCode = 400; res.end(); return; }
      try { res.setHeader("content-type", path.endsWith(".css") ? "text/css" : "application/octet-stream"); res.end(readFileSync(path)); }
      catch { res.statusCode = 404; res.end(); }
      return;
    }
    res.setHeader("content-type", "text/html");
    res.end(`<!doctype html><html><head><meta charset="utf-8"><title>Isolated theme save</title><link rel="stylesheet" href="/assets/${css}"></head><body><div id="root"></div><script src="/fixture.js"></script></body></html>`);
  });
  await new Promise<void>((done) => server.listen(0, "127.0.0.1", done));
  const address = server.address();
  if (!address || typeof address === "string") throw new Error("No fixture listener");
  const previewUrl = `http://127.0.0.1:${address.port}/`;
  console.log(JSON.stringify({ ...fixture.info, previewUrl }));
  const env = { ...process.env };
  delete env.ELECTRON_RUN_AS_NODE;
  const child = spawn(process.execPath, [
    join(root, "scripts/testing/theme-save-ui-smoke.cjs"), previewUrl,
    join(fixture.info.dataDir, "electron-profile"),
    process.env.OMB_THEME_EVIDENCE || join(root, ".omb-scratch/theme-save-evidence"),
  ], { env, stdio: "inherit", windowsHide: true });
  const status = await new Promise<number>((resolve, reject) => {
    child.once("error", reject);
    child.once("exit", (code) => resolve(code ?? 1));
  });
  if (status !== 0) throw new Error(`Theme workflow failed (${status})`);
} finally {
  await new Promise<void>((done) => server.close(() => done()));
  await fixture.close();
}
