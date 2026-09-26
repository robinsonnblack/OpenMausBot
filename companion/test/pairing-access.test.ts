import { createServer, type Server } from "node:http";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { launchVerificationServer, type VerificationServer } from "../../scripts/control-omb.ts";
import { DeviceRegistry } from "../src/devices.ts";
import { createControlServer } from "../src/control.ts";
import { createProxyHandler } from "../src/proxy.ts";
import { createConnectedDeviceTracker } from "../src/connected-devices.ts";
import { DATA_DIR } from "../src/state.ts";
import { denyReason } from "../src/routes.ts";
import { PERMISSIONS, presetPermissions } from "../src/permissions.ts";

describe("existing phone rights through isolated desktop, companion and server", () => {
  let fixture: VerificationServer;
  let proxy: Server;
  let control: Server;
  let proxyUrl: string;
  let controlUrl: string;
  let registry: DeviceRegistry;
  const connected = createConnectedDeviceTracker();
  const listen = (server: Server) => new Promise<string>(resolve => server.listen(0, "127.0.0.1", () => resolve("http://127.0.0.1:" + (server.address() as { port: number }).port)));
  const ask = async (url: string, method: string, path: string, body?: unknown, token?: string, extra: Record<string, string> = {}) => {
    const response = await fetch(url + path, { method, headers: { "content-type": "application/json", ...(token ? { authorization: "Bearer " + token } : {}), ...extra }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    return { status: response.status, body: await response.json() as any };
  };
  beforeAll(async () => {
    fixture = await launchVerificationServer();
    registry = new DeviceRegistry();
    proxy = createServer(createProxyHandler({ harnessPort: Number(new URL(fixture.info.url).port), authenticate: token => registry.authenticate(token), redeem: (code, name) => registry.redeem(code, name), serverName: () => "Isolated desktop", connected: (id, disconnect) => connected.open(id, disconnect) }));
    control = createControlServer({ get devices() { return registry; }, companionPort: 8810, discovery: () => ({ advertising: false, name: "Isolated desktop" }), connectedDeviceIds: () => connected.ids(), disconnectDevice: id => connected.disconnect(id) });
    proxyUrl = await listen(proxy);
    controlUrl = await listen(control);
  }, 60_000);
  afterAll(async () => {
    if (proxy) { proxy.closeAllConnections(); await new Promise<void>(resolve => proxy.close(() => resolve())); }
    if (control) { control.closeAllConnections(); await new Promise<void>(resolve => control.close(() => resolve())); }
    if (fixture) { console.info(JSON.stringify({ ...fixture.info, pairingAccessVerified: true })); await fixture.close(); }
  });

  it("migrates old records, promotes and downgrades the same legacy token, persists rights and disconnects streams", async () => {
    const result = registry.redeem(registry.openPairing().code, "Existing Android");
    if ("error" in result) throw new Error(result.error);
    const { token, device } = result;
    const file = join(DATA_DIR, "devices.json");
    const stored = JSON.parse(readFileSync(file, "utf8"));
    delete stored.devices.find((candidate: any) => candidate.id === device.id).access;
    writeFileSync(file, JSON.stringify(stored));
    registry = new DeviceRegistry();
    expect((await ask(proxyUrl, "GET", "/api/companion/access", undefined, token)).body).toMatchObject({ role: "client", permissions: { manageBots: false, manageSettings: false, cloudDesktop: false } });
    expect((await ask(proxyUrl, "GET", "/api/usage", undefined, token, { "x-openmausbot-companion-access": "admin" })).status).not.toBe(200);
    expect((await ask(controlUrl, "POST", "/devices/" + device.id + "/access", { access: "admin" })).status).toBe(200);
    const full = await ask(proxyUrl, "GET", "/api/companion/access", undefined, token);
    expect(full.body).toMatchObject({ role: "admin", scopes: ["admin", "client"], permissions: { manageBots: true, manageSettings: true, cloudDesktop: true } });
    expect((await ask(proxyUrl, "GET", "/api/usage", undefined, token)).status).toBe(200);
    expect(new DeviceRegistry().authenticate(token)?.access).toBe("admin");
    const stream = await fetch(proxyUrl + "/api/events", { headers: { authorization: "Bearer " + token } });
    expect(stream.status).toBe(200);
    const reader = stream.body!.getReader();
    await reader.read();
    expect(connected.ids()).toContain(device.id);
    expect((await ask(controlUrl, "POST", "/devices/" + device.id + "/access", { access: "client" })).status).toBe(200);
    const ended = (async () => { try { for (;;) { if ((await reader.read()).done) return true; } } catch (error: any) { if (error.cause?.code === "UND_ERR_SOCKET") return true; throw error; } })();
    expect(await Promise.race([ended, new Promise(resolve => setTimeout(() => resolve(false), 3000))])).toBe(true);
    expect((await ask(proxyUrl, "GET", "/api/usage", undefined, token)).status).not.toBe(200);
    expect((await ask(proxyUrl, "GET", "/api/companion/access", undefined, token)).body.role).toBe("client");
    expect(registry.authenticate(token)?.id).toBe(device.id);
    expect(new DeviceRegistry().authenticate(token)?.access).toBe("client");
  }, 20_000);

  it("shows write failures without pretending rights changed; rejects browser mutation and invalid requests", async () => {
    const device = registry.list()[0];
    const writable = registry as unknown as { persist: () => void };
    const persist = writable.persist;
    writable.persist = () => { throw new Error("ENOSPC"); };
    try {
      const failed = await ask(controlUrl, "POST", "/devices/" + device.id + "/access", { access: "admin" });
      expect(failed.status).toBe(500);
      expect(failed.body.error).toContain("previous rights remain active");
      expect(registry.list()[0].access).toBe("client");
      expect(() => registry.revoke(device.id)).toThrow("ENOSPC");
      expect(registry.list().some(candidate => candidate.id === device.id)).toBe(true);
    } finally { writable.persist = persist; }
    expect((await ask(controlUrl, "POST", "/devices/" + device.id + "/access", { access: "admin" }, undefined, { origin: "https://example.invalid" })).status).toBe(403);
    expect((await ask(controlUrl, "POST", "/devices/" + device.id + "/access", { access: "owner" })).status).toBe(400);
    expect((await ask(controlUrl, "POST", "/devices/missing/access", { access: "admin" })).status).toBe(404);
    for (const path of ["/api/auth/sessions", "/api/internal/secrets", "/api/testing/state", "/api/anything/../internal/secrets", "/api/anything/%2e%2e/internal/secrets"]) {
      expect(denyReason({ method: "GET", path, authenticated: true, access: "admin" }), path).not.toBeNull();
    }
  });

  it("persists custom rights and enforces deletion independently on both real HTTP paths", async () => {
    const phone = registry.redeem(registry.openPairing().code, "Custom fixture");
    if ("error" in phone) throw new Error(phone.error);
    const offer = await ask(fixture.info.url, "POST", "/api/auth/pairing", { label: "Custom server fixture", scopes: ["client"] });
    const accepted = await ask(fixture.info.url, "POST", "/api/auth/pair", { code: offer.body.code, label: "Custom server fixture" });
    const grants = presetPermissions("admin"); grants.messageDelete = false; grants.threadDelete = false; grants.profile = true; grants.providers = false;
    const save = async () => {
      expect((await ask(controlUrl, "POST", "/devices/" + phone.device.id + "/access", { access: "custom", permissions: grants })).status).toBe(200);
      expect((await ask(fixture.info.url, "PATCH", "/api/auth/sessions/" + accepted.body.session.id, { access: "custom", permissions: grants })).status).toBe(200);
    };
    await save();
    for (const [url, token] of [[proxyUrl, phone.token], [fixture.info.url, accepted.body.token]]) {
      const snapshot = await ask(url, "GET", "/api/companion/access", undefined, token);
      expect(snapshot.body.role).toBe("custom");
      expect(snapshot.body.capabilities).toHaveLength(PERMISSIONS.length);
      expect(snapshot.body.capabilities.find((row: any) => row.id === "threadDelete").allowed).toBe(false);
      expect((await ask(url, "POST", "/api/threads/nonexistent/messages/delete", { ids: ["m"] }, token)).status).toBe(403);
      expect((await ask(url, "DELETE", "/api/bots/nonexistent/tasks/nonexistent", undefined, token)).status).toBe(403);
      expect((await ask(url, "PATCH", "/api/config", { anthropic: { apiKey: "synthetic-fixture-value" } }, token)).status).toBe(403);
    }
    expect(new DeviceRegistry().authenticate(phone.token)?.permissions?.threadDelete).toBe(false);
    grants.threadDelete = true; await save();
    for (const [url, token] of [[proxyUrl, phone.token], [fixture.info.url, accepted.body.token]]) {
      // Passing the gate reaches the real handler, which reports the missing fixture bot.
      expect((await ask(url, "DELETE", "/api/bots/nonexistent/tasks/nonexistent", undefined, token)).status).toBe(404);
      expect((await ask(url, "POST", "/api/threads/nonexistent/messages/delete", { ids: ["m"] }, token)).status).toBe(403);
      expect((await ask(url, "PATCH", "/api/auth/sessions/" + accepted.body.session.id, { access: "admin" }, token)).status).toBe(403);
    }
    const incomplete = { ...grants }; delete (incomplete as any).threadDelete;
    expect((await ask(controlUrl, "POST", "/devices/" + phone.device.id + "/access", { access: "custom", permissions: incomplete })).status).toBe(400);
    expect((await ask(fixture.info.url, "PATCH", "/api/auth/sessions/" + accepted.body.session.id, { access: "custom", permissions: incomplete })).status).toBe(400);
  });

  it("changes a server pairing in place and prevents a restricted phone from granting itself access", async () => {
    const offer = await ask(fixture.info.url, "POST", "/api/auth/pairing", { label: "Existing server phone", scopes: ["client"] });
    expect(offer.status).toBe(200);
    const accepted = await ask(fixture.info.url, "POST", "/api/auth/pair", { code: offer.body.code, label: "Existing server phone" });
    expect(accepted.status).toBe(200);
    const { token, session } = accepted.body;
    const path = "/api/auth/sessions/" + session.id;
    expect((await ask(fixture.info.url, "GET", "/api/companion/access", undefined, token)).body.role).toBe("client");
    expect((await ask(fixture.info.url, "PATCH", path, { access: "admin" }, token)).status).toBe(403);
    expect((await ask(fixture.info.url, "PATCH", path, { access: "admin" })).status).toBe(200);
    expect((await ask(fixture.info.url, "GET", "/api/companion/access", undefined, token)).body.role).toBe("admin");
    expect((await ask(fixture.info.url, "GET", "/api/usage", undefined, token)).status).toBe(200);
    expect((await ask(fixture.info.url, "PATCH", path, { access: "client" }, token)).status).toBe(403);
    const stream = await fetch(fixture.info.url + "/api/events", { headers: { authorization: "Bearer " + token } });
    const reader = stream.body!.getReader();
    await reader.read();
    expect((await ask(fixture.info.url, "PATCH", path, { access: "client" })).status).toBe(200);
    const ended = (async () => { for (;;) { if ((await reader.read()).done) return true; } })();
    expect(await Promise.race([ended, new Promise(resolve => setTimeout(() => resolve(false), 3000))])).toBe(true);
    expect((await ask(fixture.info.url, "GET", "/api/usage", undefined, token)).status).toBe(403);
    expect((await ask(fixture.info.url, "GET", "/api/auth/session", undefined, token)).body.id).toBe(session.id);
  }, 20_000);
});
