# Personal Android and desktop builds

The `personal/release` branch is the source for both personal builds. Work on a
feature branch, test it, and merge that branch into `personal/release` to include
it in the next personal Android and Windows build. Upstream contribution PRs
can stay separate; they do not have to be merged upstream before a personal
build can contain them.

## Android

From `android/`, run `./gradlew :app:assemblePersonal` (on Windows,
`./gradlew.bat`). The APK is at
`app/build/outputs/apk/personal/app-personal.apk`. Its package name is
`com.openmausbot.companion.personal`, so it installs beside the official app.
It has its own pairing, data and theme settings. Pair it with the computer from
inside the personal app; official-app pairing links stay with the official app.

The personal build uses the local Android debug signing key. Keep that key
(`~/.android/debug.keystore`) safe: Android will only update an existing
installation with an APK signed by the same key. A CI machine's debug key will
be different. Do not publish this APK as an official release.

Quick replies start empty in the personal build. In Android Settings → Chat →
Quick replies, add any actions you actually want. In Settings → Appearance,
choose any desktop preset or edit every color role in Custom. The preset color
values are generated from `src/styles.css` by
`node scripts/export-android-skins.mjs`; run the generator after changing a
desktop palette, then `pnpm check:android-skins` in verification.

## Windows

Run `pnpm install --frozen-lockfile`, then `pnpm package:win`. The NSIS
installer appears under `release/`. Settings → Appearance includes the
ChatGPT-inspired light skin and a Custom editor for all color roles. The
personal theme choices remain local to each device.

Before handing out either installer, run the relevant tests and check the
output APK/installer exists. Keep personal builds separate from upstream
release assets and from chats, API keys, and local configuration.
