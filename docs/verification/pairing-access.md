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
