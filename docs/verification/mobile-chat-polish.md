# Android chat controls and configurable image limits

Run isolated checks from the repository root:

```powershell
pnpm exec vitest run shared/image-attachment-limits.test.ts server/turn-images.test.ts server/config.test.ts src/lib/composer-attachments.test.ts src/lib/drafts.test.ts
pnpm exec vitest run server/index.test.ts -t 'saves configurable image limits'
node --experimental-strip-types scripts/verify-image-limits.ts
```

The browser fixture mounts the real settings component against the verification
server's disposable workspace. It verifies defaults, higher values without a
fixed ceiling, persistence after reload, help on request and failed-save feedback.
Set `OMB_PLAYWRIGHT_MODULE` if Playwright is supplied externally and
`OMB_IMAGE_LIMIT_EVIDENCE` to choose its screenshot/JSON directory.

With JDK 17 and the Android SDK configured, run in `android/`:

```powershell
.\gradlew.bat :core:test --tests '*MessageAttachmentsTest' :app:testDebugUnitTest --tests '*MobileChatPolishTest' --tests '*AttachmentRulesTest' --tests '*SharePolicyTest' --tests '*ShareItemLoaderTest' --max-workers=2
```

Compose measures the full-width field and unchanged empty/first-line height,
checks growth with multiline text, equal German avatar cards, configurable
limits and persistent save feedback. A disposable MockWebServer supplies 100
messages for the expanded deletion sheet; the newest row and delete action are
visible without swiping or scrolling. Native graphics evidence is written to
`android/app/build/outputs/mobile-polish-screenshots/`.

The server fixture proves real message rejection for both configured count and
aggregate bytes and reads the persisted config. Defaults are 30 images and
60,000,000 bytes. File attachments do not consume the image aggregate allowance.
Per-file upload/type validation remains independently enforced. Settings have
no added policy maximum, within the numeric representation of each platform.

These are isolated renderer, native Compose and HTTP checks. They do not prove
physical-phone installation or a specific model provider's own image limits.
