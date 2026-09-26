export interface ImageAttachmentLimits {
  maxImages: number;
  maxTotalImageBytes: number;
}
export const DEFAULT_IMAGE_ATTACHMENT_LIMITS: ImageAttachmentLimits = {
  maxImages: 30,
  maxTotalImageBytes: 60_000_000,
};
export function imageAttachmentLimits(value?: Partial<ImageAttachmentLimits>): ImageAttachmentLimits {
  return { ...DEFAULT_IMAGE_ATTACHMENT_LIMITS, ...value };
}
export function imageAttachmentLimitError(images: readonly { size: number }[], limits: ImageAttachmentLimits, german = false): string | null {
  if (images.length > limits.maxImages) return german ? `Maximal ${limits.maxImages} Bilder pro Nachricht.` : `Send no more than ${limits.maxImages} images at a time`;
  if (images.reduce((total, image) => total + image.size, 0) > limits.maxTotalImageBytes) return german ? `Die Bilder überschreiten zusammen ${limits.maxTotalImageBytes / 1_000_000} MB.` : `Attached images exceed ${limits.maxTotalImageBytes / 1_000_000} MB total`;
  return null;
}
