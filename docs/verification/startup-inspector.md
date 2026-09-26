# Saved connections, readable settings, and optional prompt inspector

Use disposable fixtures, never the user's live app data.

Android: `./android/gradlew -p android :core:test --tests '*OnboardingTest' :app:testDebugUnitTest --tests '*OnboardingRoutingTest' --tests '*SettingsHelpLayoutTest' --tests '*ChatPreferencesTest'`.

The routing fixture holds saved-connection storage behind a deferred gate. Before it opens, the real root must show restoration, with no connect-computer action. After restoration it must show the roster. Truly unpaired first launches, revoked credentials, and explicit pairing invitations retain their routes. Settings rows give both label and value a share of width, including long labels, changing values and contextual help. The inspector setting defaults off and persists across preference instances; its switch responds immediately.

Desktop: set `OMB_VERIFY_OUTPUT` to an evidence directory, then run `electron scripts/testing/prompt-inspector-preference-smoke.mjs`. The real setting and header button are mounted in a private Electron profile. Verify hidden default, immediate switch feedback, braces icon, persistence on reload, and closure of an open inspector when disabled. No live provider or conversation is used.
