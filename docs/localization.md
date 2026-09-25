# Localization

OpenMausBot ships translations inside its desktop and Android apps. Neither app
contacts a translation service at runtime. English is the source language.
Every supported locale must cover every source key; the validation commands
fail on missing keys or changed placeholders.

## Desktop

Add new user-facing text to `src/locales/en.json` and call `t(key)` in the
component. For simple existing JSX text and attributes, the extractor can add
keys and replace literals automatically:

```sh
pnpm i18n:desktop:extract
```

The extractor handles simple JSX text and attributes. Review its diff: a
sentence assembled from multiple expressions needs a whole, contextual key
with placeholders such as `{name}`. Do not translate sentence fragments.

Draft missing or stale translations using the authenticated GPT-6 Luna Codex
CLI. The generator supplies nearby source code and related translations so the
model knows where each phrase appears, preserves placeholders, and leaves
reviewed translations intact. Run it once per supported locale:

```sh
node scripts/generate-locale-luna.mjs de German
node scripts/generate-locale-luna.mjs fr French
pnpm i18n:check
```

`pnpm i18n:check` validates complete coverage, catalog keys, placeholders,
and the English source hashes. Commit `src/locales/source-hashes.json` with
the catalogs. A changed English source invalidates its translations until they
are refreshed.

## Android

Put source text in `android/app/src/main/res/values/strings.xml` and read it
with `stringResource` or `Context.getString`. Keep format arguments positional
(`%1$s`, `%2$d`) so languages can reorder them. Generate all missing values
with the same contextual Luna workflow:

```sh
node scripts/generate-android-locale.mjs de German
node scripts/generate-android-locale.mjs fr French
pnpm i18n:android:check
```

The Android checker requires complete coverage and matching format arguments.
Build the personal APK after translating to catch Android resource syntax
errors as well.

Model output is a draft. Review ambiguous words in the actual screen and
check actions, errors, accessibility labels, and text assembled at runtime.
For German, the bot feature *Memory* is **Erinnerung** in the singular; device
storage is **Speicher**. These checks and model calls run during development,
not in the released app or GitHub Actions.
