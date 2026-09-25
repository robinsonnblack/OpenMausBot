# Localization

OpenMausBot ships its translations inside the app. It does not contact a
translation service at runtime, and contributors do not need an API key.
English in `src/locales/en.json` is the source catalog; the other JSON files
are overlays that fall back to English for older missing keys. New UI strings
must be translated in every shipped language. Android strings use
`android/app/src/main/res/values/strings.xml` and matching `values-*/strings.xml`
resources; new Android strings also need every shipped language.

## Add or update a language

1. Use a lowercase BCP-47 filename such as `de.json` or `pt-br.json`.
2. Keep every key identical to an English key and preserve placeholders such
   as `{name}` exactly, including repeated placeholders.
3. Register a new pack in `src/locales/index.ts`.
4. Run `pnpm i18n:check` and test the language from **Settings → General**.

`pnpm i18n:check` is deterministic. It validates JSON structure, unknown or
empty entries, locale filename casing, placeholder parity, and the English
source hash attached to every translated value. If English copy changes, the
old translation fails the check instead of silently looking current. The
coverage ratchet rejects new missing translations in any language, including
Android. `scripts/translation-debt.json` records only the pre-existing
gaps and their English source hashes. Updating English for an untranslated
key therefore also requires a translation. When fixing an old gap, remove
its exception with `node scripts/check-translation-coverage.mjs --update-baseline`.
Review that baseline diff: it should shrink, never acquire new exceptions.
The initial debt is substantial and must be translated before full coverage
can be claimed; this gate prevents it from growing in the meantime.

Keep visible text in catalog or Android string resources. Code literals can
escape a catalog check, so `pnpm i18n:check` also rejects new direct JSX and
Compose text literals against `scripts/ui-literal-debt.json`. This scanner
covers common static text but cannot prove that all dynamic text is localized;
reviewers must look for it in new UI. A present
translation is also not proof of correct wording: review each language's copy
and test the interface before release.

## Optional model-assisted draft

The repository includes a maintainer tool that sends missing or stale English
strings to an installed, authenticated Claude CLI. The CLI usually sends the
strings to its configured cloud model and may consume subscription or API
quota; “local” describes the CLI, not where inference runs.

```sh
# Safe, no-tools Claude mode
node scripts/generate-locale.mjs it "Italian"
```

Existing packs refresh only keys that are missing or whose English source has
changed, preserving reviewed translations for every other key. Use `--force`
only when intentionally re-drafting the whole pack. The tool rejects prose,
code fences, missing or invented keys, empty values, and changed placeholders
before it installs either file.

For a human-written or edited pack, review it and record which English source
each present value translates:

```sh
node scripts/generate-locale.mjs pt-br --accept
pnpm i18n:check
```

Commit `src/locales/source-hashes.json` with the catalog. The hash contains no
translation content or credential; it only lets CI detect stale copy.

Model output is a draft, not an authority. Review tone, terminology, grammar,
product names, and safety-sensitive copy before committing it. AI translation
does not run in GitHub Actions: normal CI stays deterministic, secret-free,
and safe for forks.
