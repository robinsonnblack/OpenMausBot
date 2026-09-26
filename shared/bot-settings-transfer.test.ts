import { describe, expect, it } from "vitest";
import { settingsTransferPatch, transferableSettings } from "./bot-settings-transfer";

describe("one-time bot settings transfer", () => {
  it("copies only the selected settings, without identity or conversation data", () => {
    const source = transferableSettings({ id: "source", name: "Vivia", voice: "voice-a", soul: "Private instructions", transcript: ["secret"] });
    const patch = settingsTransferPatch(source, ["voice"]);
    expect(patch).toEqual({ voice: "voice-a" });
    expect(source).not.toHaveProperty("id");
    expect(source).not.toHaveProperty("transcript");
  });
  it("transfers explicit off/default values instead of preserving the target's setting", () => {
    const patch = settingsTransferPatch(transferableSettings({ name: "A", speakReplies: false }), ["speakReplies", "peers", "managedSections"]);
    expect(patch).toEqual({ speakReplies: false, peers: null, managedSections: [], acknowledgePeerScope: true });
  });
  it("requires a valid nonempty setting selection and detaches mutable values", () => {
    const source = { alwaysAllow: ["read"] };
    expect(() => settingsTransferPatch(source, [])).toThrow();
    expect(() => settingsTransferPatch(source, ["id"])).toThrow();
    const patch = settingsTransferPatch(source, ["alwaysAllow"]);
    (patch.alwaysAllow as string[]).push("write");
    expect(source.alwaysAllow).toEqual(["read"]);
  });
  it("adds consent only for the reviewed permissions copied now", () => {
    expect(settingsTransferPatch({ approvalMode: "full" }, ["approvalMode"])).toEqual({ approvalMode: "full", confirmFullAccess: true, acknowledgeLocalAuto: true });
    expect(settingsTransferPatch({ voice: "v" }, ["voice"])).not.toHaveProperty("confirmFullAccess");
  });
});
