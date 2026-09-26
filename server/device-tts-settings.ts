import { constants, createCipheriv, createPublicKey, publicEncrypt, randomBytes, randomUUID } from "node:crypto";

/** Only the requested speech profile, sealed to the phone's ephemeral RSA key. */
export function sealDeviceTtsSettings(publicKey: unknown, cfg: {
  tts?: { provider?: string; key?: string; fishKey?: string; voice?: string; baseUrl?: string; model?: string };
  xai?: { key?: string };
}) {
  if (typeof publicKey !== "string" || publicKey.length > 2048) throw Error("Invalid phone encryption key");
  const key = createPublicKey({ key: Buffer.from(publicKey, "base64"), format: "der", type: "spki" });
  if (key.asymmetricKeyType !== "rsa" || key.asymmetricKeyDetails?.modulusLength !== 2048) throw Error("Invalid phone encryption key");
  const tts = cfg.tts ?? {}, provider = tts.provider ?? "elevenlabs";
  const requestId = randomUUID(), aes = randomBytes(32), iv = randomBytes(12);
  const secret = provider === "elevenlabs" ? tts.key : provider === "fish" ? tts.fishKey : provider === "xai" ? cfg.xai?.key : "";
  const payload = Buffer.from(JSON.stringify({ provider: provider === "system" ? "android" : provider,
    key: secret ?? "", voice: tts.voice ?? "", endpoint: tts.baseUrl ?? "", model: tts.model ?? "" }));
  try {
    const cipher = createCipheriv("aes-256-gcm", aes, iv); cipher.setAAD(Buffer.from(requestId));
    const ciphertext = Buffer.concat([cipher.update(payload), cipher.final(), cipher.getAuthTag()]);
    const wrappedKey = publicEncrypt({ key, padding: constants.RSA_PKCS1_OAEP_PADDING, oaepHash: "sha256" }, aes);
    return { requestId, wrappedKey: wrappedKey.toString("base64"), iv: iv.toString("base64"), ciphertext: ciphertext.toString("base64") };
  } finally { aes.fill(0); payload.fill(0); }
}
