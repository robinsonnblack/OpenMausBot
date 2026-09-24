// A loopback-only Responses relay gives each OpenMaus conversation a stable
// provider cache key even when Codex starts a fresh app-server process.
import { createHash, randomUUID } from "node:crypto";
import { createServer, type Server, type IncomingMessage } from "node:http";
import { request as httpRequest } from "node:http";
import { request as httpsRequest } from "node:https";
import { gunzipSync, inflateSync, zstdDecompressSync } from "node:zlib";

type PromptItem = { type?: string; role?: string; content?: Array<{ type?: string; text?: string }> };
type Route = { key: string; pending: Set<ReturnType<typeof httpsRequest>> };
const routes = new Map<string, Route>();
let listener: Promise<Server> | undefined;
const UPSTREAM = "https://chatgpt.com/backend-api/codex";
const MAX_REQUEST = 64 * 1024 * 1024;

export function codexCacheIdentity(botId: string, threadId: string): string {
  const hash = createHash("sha256").update(JSON.stringify(["openmaus-cache-v2", botId, threadId])).digest("hex");
  return `${hash.slice(0, 8)}-${hash.slice(8, 12)}-4${hash.slice(13, 16)}-a${hash.slice(17, 20)}-${hash.slice(20, 32)}`;
}

/** Codex may refresh its skills catalog after session start. Use the newest
 * complete copy in one stable instruction slot; keep conversation/tool order. */
export function stableCodexInput(items: PromptItem[]): PromptItem[] {
  const catalog = (part: { type?: string; text?: string }) => part.type === "input_text" &&
    typeof part.text === "string" && /^<skills_instructions>\s*## Skills\r?\n[\s\S]*?### Available skills\r?\n[\s\S]*<\/skills_instructions>\s*$/.test(part.text);
  let end = items.findIndex(item => item.type !== "additional_tools" && item.role !== "developer" && item.role !== "system" && !(item.type === "message" && item.role === "user"));
  if (end < 0) end = items.length;
  const prefix = items.slice(0, end), slots: Array<[number, number]> = [];
  prefix.forEach((item, i) => {
    if (item.type === "message" && item.role === "developer") item.content?.forEach((part, j) => { if (catalog(part)) slots.push([i, j]); });
  });
  let latest: PromptItem | undefined;
  if (slots.length) {
    const [i, j] = slots.at(-1)!;
    latest = { type: "message", role: "developer", content: [prefix[i].content![j]] };
  }
  const stale = new Map<number, Set<number>>();
  for (const [i, j] of slots) {
    if (!stale.has(i)) stale.set(i, new Set());
    stale.get(i)!.add(j);
  }
  const cleaned = prefix.flatMap((item, i) => {
    const removed = stale.get(i);
    if (!removed) return [item];
    const content = item.content!.filter((_, j) => !removed.has(j));
    return content.length ? [{ ...item, content }] : [];
  });
  const instruction = (item: PromptItem) => item.role === "developer" || item.role === "system";
  return [...cleaned.filter(instruction), ...(latest ? [latest] : []), ...cleaned.filter(item => !instruction(item)), ...items.slice(end)];
}

async function bodyOf(req: IncomingMessage): Promise<Buffer> {
  const parts: Buffer[] = []; let size = 0;
  for await (const chunk of req) {
    const part = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
    size += part.length;
    if (size > MAX_REQUEST) throw new Error("request too large");
    parts.push(part);
  }
  const bytes = Buffer.concat(parts), options = { maxOutputLength: MAX_REQUEST };
  switch (req.headers["content-encoding"]) {
    case undefined: case "identity": return bytes;
    case "zstd": return zstdDecompressSync(bytes, options);
    case "gzip": return gunzipSync(bytes, options);
    case "deflate": return inflateSync(bytes, options);
    default: throw new Error("unsupported request encoding");
  }
}

async function ensureListener(upstream = UPSTREAM): Promise<Server> {
  listener ??= new Promise((resolve, reject) => {
    const server = createServer(async (req, res) => {
      const match = /^\/([a-f0-9-]+)(\/responses(?:\/compact)?)$/.exec(req.url ?? "");
      const route = match && routes.get(match[1]);
      if (!route || req.method !== "POST") { res.writeHead(404).end(); return; }
      let encoded: Buffer;
      try {
        const input = JSON.parse((await bodyOf(req)).toString("utf8")) as { input?: PromptItem[]; prompt_cache_key?: string };
        input.prompt_cache_key = route.key;
        if (Array.isArray(input.input)) input.input = stableCodexInput(input.input);
        encoded = Buffer.from(JSON.stringify(input));
      } catch { res.writeHead(400, { "content-type": "application/json" }).end('{"error":"Invalid provider request"}'); return; }
      if (!routes.has(match[1])) { res.writeHead(410).end(); return; }
      const headers: Record<string, string | string[] | undefined> = { ...req.headers, host: new URL(upstream).host, "session-id": route.key,
        "content-length": String(encoded.length) };
      for (const key of ["connection", "proxy-authorization", "proxy-connection", "content-encoding", "transfer-encoding"]) delete headers[key];
      const target = upstream + match[2];
      const request = target.startsWith("https:") ? httpsRequest : httpRequest;
      const upstreamReq = request(target, { method: "POST", headers }, reply => {
        res.writeHead(reply.statusCode ?? 502, reply.headers);
        reply.pipe(res);
      });
      route.pending.add(upstreamReq);
      upstreamReq.once("close", () => route.pending.delete(upstreamReq));
      upstreamReq.on("error", () => {
        if (!res.headersSent) res.writeHead(502, { "content-type": "application/json" }).end('{"error":"Provider connection interrupted"}');
        else res.destroy();
      });
      upstreamReq.setTimeout(300_000, () => upstreamReq.destroy());
      req.once("aborted", () => upstreamReq.destroy());
      res.once("close", () => { if (!res.writableEnded) upstreamReq.destroy(); });
      upstreamReq.end(encoded);
    });
    server.once("error", error => { listener = undefined; reject(error); });
    server.listen(0, "127.0.0.1", () => { server.unref(); resolve(server); });
  });
  return listener;
}

export async function openCodexCacheRoute(botId: string, threadId: string, upstream = UPSTREAM) {
  const server = await ensureListener(upstream), id = randomUUID();
  const route: Route = { key: codexCacheIdentity(botId, threadId), pending: new Set() };
  routes.set(id, route);
  return {
    provider: { name: "OpenAI", base_url: `http://127.0.0.1:${(server.address() as { port: number }).port}/${id}`,
      wire_api: "responses", requires_openai_auth: true, supports_websockets: false },
    close() {
      routes.delete(id);
      for (const request of route.pending) request.destroy();
      route.pending.clear();
    },
  };
}
