import { createServer } from "node:http";
import { describe, expect, it } from "vitest";
import { codexCacheIdentity, openCodexCacheRoute, stableCodexInput } from "./codex-cache-route.ts";

const skills = (label: string) => `<skills_instructions>\n## Skills\n### Available skills\n- ${label}\n</skills_instructions>`;

describe("Codex cache route", () => {
  it("keeps the newest skills catalog in a stable instruction prefix", () => {
    const input = [
      { type: "message", role: "developer", content: [{ type: "input_text", text: "instructions" }, { type: "input_text", text: skills("old") }] },
      { type: "message", role: "user", content: [{ type: "input_text", text: "prior message" }] },
      { type: "message", role: "developer", content: [{ type: "input_text", text: skills("new") }] },
      { type: "message", role: "assistant", content: [{ type: "input_text", text: "reply" }] },
    ];
    const ordered = stableCodexInput(input);
    expect(JSON.stringify(ordered)).not.toContain("old");
    expect(ordered[0].content?.[0].text).toBe("instructions");
    expect(ordered[1].content?.[0].text).toBe(skills("new"));
    expect(ordered[2].content?.[0].text).toBe("prior message");
    expect(ordered.at(-1)).toEqual(input.at(-1));
  });

  it("forwards repeated requests with identical cache identity and preserves auth", async () => {
    const seen: Array<{ body: any; authorization: string | undefined; session: string | undefined }> = [];
    const upstream = createServer(async (req, res) => {
      let raw = "";
      for await (const part of req) raw += part;
      seen.push({ body: JSON.parse(raw), authorization: req.headers.authorization, session: req.headers["session-id"] as string | undefined });
      res.writeHead(200, { "content-type": "application/json" }).end('{"ok":true}');
    });
    await new Promise<void>(resolve => upstream.listen(0, "127.0.0.1", resolve));
    const address = upstream.address();
    if (!address || typeof address === "string") throw new Error("missing test server address");
    const first = await openCodexCacheRoute("bot", "thread", `http://127.0.0.1:${address.port}`);
    const second = await openCodexCacheRoute("bot", "thread", `http://127.0.0.1:${address.port}`);
    try {
      for (const route of [first, second]) {
        const response = await fetch(`${route.provider.base_url}/responses`, { method: "POST", headers: {
          authorization: "Bearer fixture", "content-type": "application/json",
        }, body: JSON.stringify({ input: [{ type: "message", role: "user", content: [{ type: "input_text", text: "hello" }] }] }) });
        expect(response.status).toBe(200);
      }
      expect(seen).toHaveLength(2);
      expect(seen[0].body).toEqual(seen[1].body);
      expect(seen.map(row => row.body.prompt_cache_key)).toEqual([codexCacheIdentity("bot", "thread"), codexCacheIdentity("bot", "thread")]);
      expect(seen.every(row => row.authorization === "Bearer fixture" && row.session === codexCacheIdentity("bot", "thread"))).toBe(true);
    } finally { first.close(); second.close(); upstream.close(); }
  });
});
