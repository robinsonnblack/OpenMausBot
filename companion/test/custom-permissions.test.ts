import { describe, it, expect } from "vitest";
import { PERMISSIONS, presetPermissions, permissionDenial, permissionFor, configPermissionDenial } from "../src/permissions.ts";
import { denyReason } from "../src/routes.ts";

describe("complete per-action phone permissions", () => {
  const actions = [
    ["chatRead", "GET", "/api/threads/t/messages"],
    ["chatEdit", "POST", "/api/bots/b/messages/m/edit"],
    ["threadCreate", "POST", "/api/bots/b/tasks"],
    ["threadEdit", "PATCH", "/api/groups/g/tasks/t"],
    ["messageDelete", "POST", "/api/threads/t/messages/delete"],
    ["threadDelete", "DELETE", "/api/bots/b/tasks/t"],
    ["threadDelete", "DELETE", "/api/groups/g/tasks/t"],
    ["chatSend", "POST", "/api/bots/b/messages"],
    ["approvals", "POST", "/api/threads/t/respond"],
    ["routines", "DELETE", "/api/routines/r"],
    ["routineRun", "POST", "/api/routines/r/run"],
    ["botDelete", "DELETE", "/api/bots/b"],
    ["botCreate", "POST", "/api/bots"],
    ["botEdit", "PATCH", "/api/bots/b/profile"],
    ["groups", "POST", "/api/groups"],
    ["groupDelete", "DELETE", "/api/groups/g"],
    ["teams", "POST", "/api/sidebar-sections"],
    ["attachments", "POST", "/api/files"],
    ["export", "GET", "/api/threads/t/export"],
    ["voice", "POST", "/api/tts/speak"],
    ["connectors", "DELETE", "/api/connectors/app/accounts/account"],
    ["memory", "DELETE", "/api/bots/b/memory/file"],
    ["skills", "DELETE", "/api/bots/b/skills/s"],
    ["promptInspector", "GET", "/api/threads/t/prompt-inspector"],
    ["profile", "PATCH", "/api/config/profile"],
    ["engines", "POST", "/api/instances/codex/auth/start"],
    ["browser", "POST", "/api/browser-engine/install"],
    ["localVm", "POST", "/api/local-computer/start"],
    ["usage", "GET", "/api/usage"],
    ["budgets", "PATCH", "/api/billing"],
    ["webhooks", "DELETE", "/api/webhooks/w"],
    ["workspace", "PATCH", "/api/settings/other"],
    ["backups", "POST", "/api/workspace-backup/restore"],
    ["providers", "POST", "/api/keys/test"],
    ["cloudDesktop", "POST", "/api/bots/b/computer/join"],
  ] as const;
  it.each(actions)("enforces %s independently for %s %s on both boundaries", (id, method, path) => {
    const grants = presetPermissions("custom");
    expect(permissionFor(method, path)).toBe(id);
    expect(permissionDenial(method, path, "custom", grants)).toContain(id);
    expect(denyReason({ method, path, authenticated: true, access: "custom", permissions: grants })?.status).toBe(403);
    grants[id] = true;
    expect(permissionDenial(method, path, "custom", grants)).toBeNull();
    expect(denyReason({ method, path, authenticated: true, access: "custom", permissions: grants })).toBeNull();
  });
  it("full means all catalogue rights; chat excludes deletion and administration", () => {
    expect(Object.values(presetPermissions("admin")).every(Boolean)).toBe(true);
    for (const [id, method, path] of actions) {
      if (["chatRead", "chatSend", "chatEdit", "threadCreate", "threadEdit", "approvals", "attachments", "voice"].includes(id)) expect(permissionDenial(method, path, "client")).toBeNull();
      else expect(permissionDenial(method, path, "client")).not.toBeNull();
    }
    expect(new Set(PERMISSIONS.map(row => row[0])).size).toBe(PERMISSIONS.length);
    expect(new Set(actions.map(row => row[0])).size).toBe(PERMISSIONS.length);
  });
  it("does not let profile access write credentials or bypass other config rights", () => {
    const grants = presetPermissions("custom"); grants.profile = true;
    expect(configPermissionDenial({ profile: { name: "Fixture" } }, grants)).toBeNull();
    expect(configPermissionDenial({ anthropic: { apiKey: "synthetic" } }, grants)).toContain("providers");
    expect(configPermissionDenial({ profile: {}, budgets: {} }, grants)).toContain("budgets");
    grants.browser = true;
    expect(configPermissionDenial({ features: { browser: true, other: true } }, grants)).toContain("workspace");
    expect(configPermissionDenial({ features: { skillAuthoring: true } }, grants)).toContain("skills");
    expect(permissionDenial("PATCH", "/api/auth/sessions/other", "custom", presetPermissions("admin"))).not.toBeNull();
  });
});
