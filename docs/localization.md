# Localization

OpenMausBot ships its translations inside the app. It does not contact a
translation service at runtime, and contributors do not need an API key.
English in `src/locales/en.json` is the source catalog; the other JSON files
are partial overlays that fall back to English for missing keys.

## Add or update a language

1. Use a lowercase BCP-47 filename such as `de.json` or `pt-br.json`.
2. Keep every key identical to an English key and preserve placeholders such
   as `{name}` exactly, including repeated placeholders.
3. Register a new pack in `src/locales/index.ts`.
4. Run `pnpm i18n:check` and test the language from **Settings → General**.

`pnpm i18n:check` is deterministic. It validates JSON structure, unknown or
empty entries, locale filename casing, placeholder parity, and the English
source hash attached to every translated value. If English copy changes, the
old translation fails the check instead of silently looking current. Missing
translations remain allowed because the runtime has an English fallback.

## Optional model-assisted draft

The repository includes a maintainer tool that sends missing or stale English
strings to GPT-6 Luna through an installed, authenticated Codex CLI. This uses
the maintainer's Codex allowance. It does not run in CI. Strings are grouped by
UI section and supplied with related English copy, established translations,
and code usage, so ambiguous labels are translated in their screen context.
The CLI runs in ephemeral, read-only mode in a temporary directory.

```sh
# Contextual Luna draft
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

For a second contextual pass, run `node scripts/review-locale.mjs de German`.
Use `--term 'thread|team|provider|approval'` to inspect ambiguous product terms
first. The reviewer may return no corrections. It writes proposed changes to
`translation-reviews/de.json` without changing the catalog. Inspect each
proposal against the English copy and the surrounding screen; a different
wording is not necessarily an improvement. Apply only a verified proposal with
`node scripts/review-locale.mjs de German --apply --apply-key key.name`, then
run `node scripts/generate-locale.mjs de --accept` and `pnpm i18n:check`.
