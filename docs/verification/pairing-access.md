# Existing phone connection rights

Run the API regression against a disposable fake-engine home:

```sh
pnpm exec vitest run companion/test/pairing-access.test.ts server/sessions.test.ts server/request-auth.test.ts
```

It migrates a legacy companion record without a role, promotes and downgrades
its existing token, verifies a real administrative route, restarts the registry,
and checks stream termination. The server-session pairing follows the same
in-place change. Restricted devices cannot promote themselves. Failed persistence
keeps the prior permissions and reports a concrete error. Forged access headers
cannot authorize a request across the private Electron relay boundary.

Exercise the production Desktop controls in an isolated browser:

```sh
pnpm build
node --experimental-strip-types scripts/verify-pairing-access.ts
```

Set `OMB_PLAYWRIGHT_MODULE` to the installed Playwright module when it is outside
the project's dependencies; `OMB_PAIRING_EVIDENCE` chooses the screenshot/report
directory. Both real Settings components use disposable registries and the fake
server. This checks saved feedback, effective roles, persistence after reload,
and a failed write with the old rights still shown. It never changes a user's
paired devices.

Android's `SessionTest` uses real HTTP requests to verify live rights for a legacy
pairing, unchanged connection/token, promotion and downgrade, a specific missing
endpoint error, inconsistent replies, and a response arriving after unpairing.
`SettingsPolicyTest` covers display policy. Build the personal APK with
`./gradlew :core:test :app:testDebugUnitTest :app:assemblePersonal`.

The phone grants are app workspace permissions, not Android root or OS
permissions. Cloud desktop control remains a separate companion permission.
Existing legacy grants migrate to the previously enforced restricted access.
Changing rights does not replace the credential, require USB debugging, or
require re-pairing. An old Android app must first be updated to display live
permissions. This recipe does not establish installation on a physical phone.
# Custom permissions and compact phone UI

Version personal.8 uses `companion/src/permissions.ts` as the authoritative
catalogue for both pairing paths. Full access enables all 34 catalogue entries;
chat and approvals excludes deletion and administration. Custom records persist
each boolean without changing the pairing token. Existing screen-control
exceptions migrate to custom grants so a preset label never hides an exception.

Run `pnpm exec vitest run companion/test server/sessions.test.ts server/request-auth.test.ts`.
The custom-permissions tests exercise every catalogue entry. The real HTTP
fixture proves independent message/thread deletion gates, persisted grants,
complete snapshots, rejection of incomplete editors and self-escalation, and
field-level protection of configuration writes.

After `pnpm build`, run `node --experimental-strip-types scripts/verify-pairing-access.ts`
and `node --experimental-strip-types scripts/verify-theme-save.ts`. The desktop
fixture opens both real custom editors, checks all entries and explicit help,
proves save feedback and retained values after reload, and injects a write
failure. Theme geometry is checked at 1280, 900, 560 and 360 pixels.

Android checks: `:core:test --tests '*SessionTest'` and `:app:testDebugUnitTest`
with `*PairingAccessDisplayTest`, `*SettingsReconnectWiringTest` and
`*SettingsPolicyTest`. The Compose tests prove compact settings, permission
details and help opened explicitly, immediate busy/disabled feedback, identical
successful refreshes on successive taps, and error feedback without false success.
These tests do not claim control of or installation on a physical phone.
