# Saved connections, readable settings, and optional prompt inspector

Use disposable fixtures, never the user's live app data.

Android: `./android/gradlew -p android :core:test --tests '*OnboardingTest' :app:testDebugUnitTest --tests '*OnboardingRoutingTest' --tests '*SettingsHelpLayoutTest' --tests '*ChatPreferencesTest'`.

The routing fixture holds saved-connection storage behind a deferred gate. Before it opens, the real root must show restoration, with no connect-computer action. After restoration it must show the roster. Truly unpaired first launches, revoked credentials, and explicit pairing invitations retain their routes. Settings rows give both label and value a share of width, including long labels, changing values and contextual help. The inspector setting defaults off and persists across preference instances; its switch responds immediately.

Desktop: set `OMB_VERIFY_OUTPUT` to an evidence directory, then run `electron scripts/testing/prompt-inspector-preference-smoke.mjs`. The real setting and header button are mounted in a private Electron profile. Verify hidden default, immediate switch feedback, braces icon, persistence on reload, and closure of an open inspector when disabled. No live provider or conversation is used.

## Personal release integration

Desktop `0.1.88-personal.14` incorporates the complete released `v0.1.88` history; Android `1.3.0-personal.14` also incorporates `android-v1.3.0`. Both upstream tags must remain ancestors of the release commit. Merge rather than copying the upstream tree, retaining personal themes, permissions, speech-input preferences, and image-limit configuration.

Run the companion proxy, custom-permission, pairing-access and route suites, server speech-import/image/message-deletion suites, and source/packaged agents-catalog wire checks. The catalog includes the personal room activity-limit schema in addition to the new upstream tools; regenerated golden payloads and byte budgets must reflect both. Voice key changes require the existing `providers` permission and bounded upstream voice validation. Test that an ungranted device remains denied, and a separately granted fixture succeeds.

Android regression fixtures cover both composer and call dictation. Calls choose their own silence-turn settings in memory without overwriting the saved composer stop/send preferences. The thread-navigation fixture supplies an explicit verified grant; it must not weaken production access enforcement. Check voice-settings layout, saving/success/error feedback, locale completeness, and the real production theme workflow again after integration.

Before replacing an installation, back up desktop data, pairing state and preferences. Compare the packaged source/resources with the reviewed build and verify published asset hashes. Update the same signed Android application ID with `adb install -r`; never uninstall it or clear its data for this update. Live microphone calls still require separate device validation.

## Legacy bot defaults and workspace browser recovery

Run `vitest run server/config.test.ts server/new-bot-defaults.test.ts src/components/PlaceChip.browser.test.ts src/components/ComputerPanel.browser.test.ts src/components/browser-install-opt-in.test.ts src/lib/feature-flags.test.ts`, then `node --experimental-strip-types scripts/verify-legacy-config.ts`.

The real server fixture seeds only its own temporary configuration with flat legacy bot defaults and an enabled browser. Check that startup retains the browser opt-in, language and named fake provider. A normal settings PATCH must write canonical defaults without losing unrelated settings or replaying one-time consent. The storage-only converter accepts the old shape; current API writes remain strict, canonical values/clears win, and malformed values still fail validation. The selector probe runs the actual availability hook with the parsed legacy configuration and a supported engine. Real opt-outs and unsupported engines must remain unavailable. Loading alone does not rewrite the person's configuration file.
