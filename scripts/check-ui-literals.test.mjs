import { describe, expect, it } from "vitest";
import { androidLiterals, desktopLiterals, newLiteralErrors } from "./check-ui-literals.mjs";

describe("new visible UI text guard", () => {
  it("finds direct JSX text and visible attributes", () => {
    expect(desktopLiterals("Test.tsx", '<div title="Help">Hello</div>'))
      .toEqual([["title", "Help"], ["text", "Hello"]]);
  });

  it("finds direct Android Compose text", () => {
    expect(androidLiterals('Text("Hello")\nText(text = "Ready")'))
      .toEqual([["Text", "Hello"], ["Text", "Ready"]]);
  });

  it("allows old literals but rejects new occurrences", () => {
    expect(newLiteralErrors({ old: 1, added: 1 }, { old: 1 }))
      .toEqual(["New hardcoded UI text (1): added"]);
  });

  it("requires the exception list to shrink when direct text is removed", () => {
    expect(newLiteralErrors({}, { old: 1 }))
      .toEqual(["Resolved hardcoded UI text; shrink the debt baseline: old"]);
  });
});
