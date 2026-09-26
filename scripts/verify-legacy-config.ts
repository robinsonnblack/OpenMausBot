import assert from "node:assert/strict";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { launchVerificationServer } from "./control-omb.ts";

const fixture = await launchVerificationServer(process.env, undefined, undefined, undefined, undefined, undefined, [], undefined, (dataDir) => {
  const file = join(dataDir, "config.json");
  const config = JSON.parse(readFileSync(file, "utf8"));
  config.features = { browser: true };
  config.language = "de";
  config.newBotDefaults = { name: "Fixture", notifications: false, approvalMode: "full", confirmFullAccess: true, autoApprove: true, _routines: [] };
  writeFileSync(file, JSON.stringify(config));
});
try {
  const get = async (path: string) => {
    const response = await fetch(fixture.info.url + path);
    assert.equal(response.status, 200);
    return response.json();
  };
  const config = await get("/api/config");
  assert.equal(config.features.browser, true);
  assert.equal(config.language, "de");
  const instances = await get("/api/instances");
  assert.equal(instances.instances.find((instance: { instanceId: string }) => instance.instanceId === "claude")?.displayName, "Verification fixture");
  const update = await fetch(fixture.info.url + "/api/config", {
    method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ features: { showToolCalls: true } }),
  });
  assert.equal(update.status, 200);
  const saved = JSON.parse(readFileSync(join(fixture.info.dataDir, "config.json"), "utf8"));
  assert.equal(saved.features.browser, true);
  assert.equal(saved.newBotDefaults.profile.name, "Fixture");
  assert.equal(saved.newBotDefaults.profile.confirmFullAccess, undefined);
  assert.equal((await get("/api/config")).features.browser, true);
  console.log("Isolated real server: legacy defaults loaded; browser and providers retained; normal save converts format without granting consent.");
} finally {
  await fixture.close();
}
