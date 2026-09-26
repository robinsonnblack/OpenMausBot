import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";
import { parseStoredConfig } from "../../server/config.ts";
import type { FeatureFlagConfig } from "@/lib/feature-flags";
import type { Bot } from "@/state/store";

const fixture = vi.hoisted(() => ({ config: {} as FeatureFlagConfig, browserMcp: true }));
vi.mock("@/state/store", () => ({ useStore: () => ({ state: {
  config: fixture.config,
  instances: [{ instanceId: "fixture", driverKind: "codex", capabilities: { browserMcp: fixture.browserMcp, computerMcp: true } }],
} }) }));
vi.mock("./DesktopCapabilities", () => ({ useDesktopCapabilities: () => ({ capabilities: {} }) }));
vi.mock("@/lib/local-computer", () => ({ instanceSupportsLocalComputer: () => true, localComputerSelectable: () => true }));
import { usePlaceAvailability } from "./PlaceChip";

const bot = { modelSelection: { instanceId: "fixture" } } as Bot;
function Probe() { return createElement("span", null, String(usePlaceAvailability(bot).browser)); }
const available = () => renderToStaticMarkup(createElement(Probe));

describe("browser destination after loading legacy configuration", () => {
  it("keeps the browser selectable for a supported engine with flat stored bot defaults", () => {
    fixture.config = { ...parseStoredConfig({ features: { browser: true }, newBotDefaults: { name: "Fixture", confirmFullAccess: true, _routines: [] } }), browserEngine: { kind: "engine" } };
    fixture.browserMcp = true;
    expect(available()).toBe("<span>true</span>");
  });
  it("still respects a real workspace opt-out and an unsupported engine", () => {
    fixture.config = { features: { browser: false }, browserEngine: { kind: "engine" } };
    fixture.browserMcp = true;
    expect(available()).toBe("<span>false</span>");
    fixture.config.features = { browser: true };
    fixture.browserMcp = false;
    expect(available()).toBe("<span>false</span>");
  });
});
