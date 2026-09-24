import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { DatabaseSync } from "node:sqlite";
import { expect, it } from "vitest";
import { launchVerificationServer, runControlOmb } from "../scripts/control-omb.ts";
import { fixtureApi } from "../scripts/testing/preview-fixture.ts";

it("deletes selected messages and stored references from an isolated conversation", async () => {
  const fixture = await launchVerificationServer();
  const api = fixtureApi(fixture.info.url);
  const control = (...args: string[]) => runControlOmb([...args, "--url", fixture.info.url]);
  try {
    const { bot } = await control("new-bot", "--name", "Deletion fixture") as { bot: { id: string } };
    const saved = (await api("GET", "/api/bots?messages=0")).bots.find((item: { id: string }) => item.id === bot.id);
    const threadId = saved.tasks[0].threadId as string;
    const secret = "fixture-only-deletion-phrase";
    await control("send", "--bot", bot.id, "--text", secret);
    await control("wait", "--bot", bot.id, "--timeout", "30");
    const transcript = await api("GET", `/api/threads/${threadId}/messages?limit=100`);
    const message = transcript.messages.find((item: { text?: string }) => item.text === secret);
    expect(message?.id).toBeTruthy();
    const selection = await api("GET", `/api/threads/${threadId}/message-selection`);
    expect(selection.allIds).toContain(message.id);
    const result = await api("POST", `/api/threads/${threadId}/messages/delete`, { ids: [message.id] });
    expect(result).toMatchObject({ ok: true, deleted: 1, ids: [message.id] });
    expect((await api("GET", `/api/threads/${threadId}/messages?limit=100`)).messages.some((item: { id: string }) => item.id === message.id)).toBe(false);
    const db = new DatabaseSync(join(fixture.info.dataDir, "messages.db"), { readOnly: true });
    try { expect(db.prepare("SELECT COUNT(*) AS count FROM messages WHERE thread_id=? AND id=?").get(threadId, message.id)?.count).toBe(0); }
    finally { db.close(); }
    const capture = join(fixture.info.dataDir, "prompt-inspector", `${threadId}.json`);
    if (existsSync(capture)) expect(readFileSync(capture, "utf8")).not.toContain(secret);
    const repeat = await fetch(`${fixture.info.url}/api/threads/${threadId}/messages/delete`, {
      method: "POST", headers: { "content-type": "application/json", origin: fixture.info.url }, body: JSON.stringify({ ids: [message.id] }),
    });
    expect(repeat.status).toBe(404);
  } finally { await fixture.close(); }
}, 180_000);
