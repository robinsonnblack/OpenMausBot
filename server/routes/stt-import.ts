import { randomUUID } from "node:crypto";
import { PASS, type RouteHandler } from "./table.ts";
import { effectivePermissions } from "../../companion/src/permissions.ts";
interface Pending { resolve(value: unknown): void; reject(reason: Error): void; timer: ReturnType<typeof setTimeout>; device: string }
export class SttImportBridge {
  private pending = new Map<string, Pending>();
  private send: (message: { type: "openmausbot:stt-import"; requestId: string; deviceId: string; publicKey: string; expiresAt: number }) => boolean;
  constructor(send: SttImportBridge["send"]) { this.send = send; }
  receive(raw: unknown): boolean {
    if (!raw || typeof raw !== "object") return false;
    const message = raw as Record<string, unknown>;
    if (message.type !== "openmausbot:stt-import-result") return false;
    const item = this.pending.get(String(message.requestId));
    if (!item) return true;
    this.pending.delete(String(message.requestId)); clearTimeout(item.timer);
    if (message.envelope) item.resolve(message.envelope); else item.reject(new Error(typeof message.error === "string" ? message.error : "Import failed."));
    return true;
  }
  request(deviceId: string, publicKey: string): Promise<unknown> {
    if (this.pending.size >= 3 || [...this.pending.values()].some(p => p.device === deviceId)) return Promise.reject(new Error("An import is already pending. Finish it on the desktop."));
    return new Promise((resolve, reject) => {
      const requestId = randomUUID();
      const timer = setTimeout(() => { this.pending.delete(requestId); reject(new Error("The desktop did not confirm the import. Try again.")); }, 90000);
      this.pending.set(requestId, { resolve, reject, timer, device: deviceId });
      if (!this.send({ type: "openmausbot:stt-import", requestId, deviceId, publicKey, expiresAt: Date.now() + 90000 })) {
        clearTimeout(timer); this.pending.delete(requestId); reject(new Error("Update the desktop app to use STT import."));
      }
    });
  }
}
export function createSttImportRoutes(bridge: SttImportBridge): RouteHandler {
  return async ({ req, res, path, method, auth, json, readBody }) => {
    if (method !== "POST" || path !== "/api/transcription/import") return PASS;
    res.setHeader("cache-control", "no-store");
    const deviceId = req.headers["x-openmausbot-companion-device"];
    const grants = auth.kind === "loopback" ? auth.permissions : effectivePermissions(auth.session.access ?? (auth.scopes.includes("admin") ? "admin" : "client"), auth.session.permissions);
    if (req.headers["x-openmausbot-companion"] !== "1" || typeof deviceId !== "string" || !/^[\w-]{1,128}$/.test(deviceId) || !grants?.providers) return json(res, 403, { error: "STT import requires provider access for this paired phone." });
    const body = await readBody(req, 4096);
    if (typeof body.publicKey !== "string" || body.publicKey.length > 2048 || !/^[A-Za-z0-9+/]+={0,2}$/.test(body.publicKey)) return json(res, 400, { error: "Invalid phone encryption key." });
    try { return json(res, 200, await bridge.request(deviceId, body.publicKey)); }
    catch (error) { return json(res, 409, { error: error instanceof Error ? error.message : "Import failed." }); }
  };
}
