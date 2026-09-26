import { constants, createDecipheriv, generateKeyPairSync, privateDecrypt } from "node:crypto";
import { describe, expect, it } from "vitest";
import { sealDeviceTtsSettings } from "./device-tts-settings.ts";

describe("phone voice credential import", () => {
  it("seals only the selected provider profile to the requesting phone", () => {
    const phone = generateKeyPairSync("rsa", { modulusLength: 2048 });
    const publicKey = phone.publicKey.export({ format: "der", type: "spki" }).toString("base64");
    const envelope = sealDeviceTtsSettings(publicKey, { tts: { provider: "fish", fishKey: "fish-secret", key: "other-secret", voice: "v" }, xai: { key: "third-secret" } });
    expect(JSON.stringify(envelope)).not.toContain("secret");
    const key = privateDecrypt({ key: phone.privateKey, padding: constants.RSA_PKCS1_OAEP_PADDING, oaepHash: "sha256" }, Buffer.from(envelope.wrappedKey, "base64"));
    const cipher = createDecipheriv("aes-256-gcm", key, Buffer.from(envelope.iv, "base64"));
    cipher.setAAD(Buffer.from(envelope.requestId));
    const ciphertext = Buffer.from(envelope.ciphertext, "base64");
    cipher.setAuthTag(ciphertext.subarray(-16));
    const payload = JSON.parse(Buffer.concat([cipher.update(ciphertext.subarray(0, -16)), cipher.final()]).toString());
    expect(payload).toEqual({ provider: "fish", key: "fish-secret", voice: "v", endpoint: "", model: "" });
    const wrongPhone = generateKeyPairSync("rsa", { modulusLength: 2048 });
    expect(() => privateDecrypt({ key: wrongPhone.privateKey, oaepHash: "sha256" }, Buffer.from(envelope.wrappedKey, "base64"))).toThrow();
  });
  it("rejects malformed and unsuitable public keys", () => {
    expect(() => sealDeviceTtsSettings("not-a-key", {})).toThrow();
    const weak = generateKeyPairSync("rsa", { modulusLength: 1024 });
    expect(() => sealDeviceTtsSettings(weak.publicKey.export({ format: "der", type: "spki" }).toString("base64"), {})).toThrow();
  });
});
