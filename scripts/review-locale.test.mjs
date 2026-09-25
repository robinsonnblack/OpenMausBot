import { describe, expect, it } from "vitest";

import { main, validateCorrections } from "./review-locale.mjs";

const source = { "pairing.connect": "Connect {device} to this computer" };
const translated = { "pairing.connect": "{device} mit diesem Gerät verbinden" };

describe("contextual locale review", () => {
  it("accepts a review with no corrections", () => {
    expect(() => validateCorrections([], new Set(["pairing.connect"]), source, translated)).not.toThrow();
  });

  it("requires selecting one reviewed proposal before applying it", () => {
    expect(() => main(["de", "German", "--apply"])).toThrow(/apply-key/);
  });

  it("accepts a corrected meaning while preserving placeholders", () => {
    expect(() => validateCorrections([
      { key: "pairing.connect", replacement: "{device} mit diesem Computer verbinden", reason: "The target is the computer." },
    ], new Set(["pairing.connect"]), source, translated)).not.toThrow();
  });

  it("rejects lost placeholders and unrelated keys", () => {
    expect(() => validateCorrections([
      { key: "pairing.connect", replacement: "Mit diesem Computer verbinden", reason: "Shorter" },
    ], new Set(["pairing.connect"]), source, translated)).toThrow(/placeholder/);
    expect(() => validateCorrections([
      { key: "other", replacement: "Anders", reason: "Different" },
    ], new Set(["pairing.connect"]), source, translated)).toThrow(/unknown/);
  });
});
