import { describe, expect, it } from "vitest";
import { androidStrings, validateCoverage } from "./check-translation-coverage.mjs";

describe("translation coverage ratchet", () => {
  const source = { existing: "Existing", added: "New {name}" };
  const existingHash = "0a597385b8bebf6f72e9528351fa0e58c9fe4d02bf5041e3154c32ce2970cd35";

  it("rejects a newly untranslated key while grandfathering the existing omission", () => {
    const errors = validateCoverage(source, { de: {} }, { de: { existing: existingHash } }, "desktop");
    expect(errors).toContain("desktop/de: added needs a translation");
    expect(errors).not.toContain("desktop/de: existing needs a translation");
  });

  it("requires exceptions to be removed when a translation is added", () => {
    expect(validateCoverage(source, { de: { existing: "Vorhanden", added: "Neu {name}" } },
      { de: { existing: existingHash } }, "desktop"))
      .toEqual(["desktop/de: existing is translated; remove its debt exception"]);
  });

  it("rejects changed source text behind an old exception", () => {
    expect(validateCoverage({ existing: "Changed" }, { de: {} },
      { de: { existing: existingHash } }, "desktop"))
      .toEqual(["desktop/de: existing needs a translation"]);
  });

  it("reads translatable Android strings and ignores the product name", () => {
    expect(androidStrings('<resources><string name="app_name">MausBot</string><string name="new_label">New</string></resources>'))
      .toEqual({ new_label: "New" });
  });
});
