import { expect, it } from "vitest";
import { imageAttachmentLimits, imageAttachmentLimitError } from "./image-attachment-limits";
it("uses 30 images and decimal 60 MB, including exact byte boundaries", () => {
  const limits = imageAttachmentLimits();
  expect(limits).toEqual({ maxImages: 30, maxTotalImageBytes: 60_000_000 });
  expect(imageAttachmentLimitError(Array.from({ length: 30 }, () => ({ size: 2_000_000 })), limits)).toBeNull();
  expect(imageAttachmentLimitError([{ size: 60_000_001 }], limits)).toContain("60 MB");
  expect(imageAttachmentLimitError(Array.from({ length: 31 }, () => ({ size: 1 })), limits)).toContain("30 images");
  expect(imageAttachmentLimitError(Array.from({ length: 75 }, () => ({ size: 1 })), imageAttachmentLimits({ maxImages: 100000, maxTotalImageBytes: 1_000_000_000_000 }))).toBeNull();
});
