# OpenMausBot Android companion

The Android counterpart to the iOS companion app: pair a phone with a computer
running OpenMausBot, then read and answer from the phone.

With an admin-scoped pairing, Settings → Shared profile lets the phone edit
the paired computer's name, email and "About me" text, which are shared with
all bots. The editor checks for a change made elsewhere before saving. A client-scoped
pairing cannot read or edit that text.

Admin-paired phones can manage teams under Settings: rename a team, move bots
into or out of it, or remove the team heading without deleting bots or chats.
The server checks team membership conflicts before applying a change.

The Android New bot sheet loads the paired computer's suggested defaults and
available model catalog before creation. The person can choose provider,
model, reasoning effort and team before the first turn; the server validates
that the selected model is available.
The optional Bot settings section inherits the computer's saved creation
defaults and lets an admin set standing instructions, notifications, spoken
replies, color, computer destination, Ask/Auto approval, working folder and
voice before creating the bot. A named or temporary browser profile can also
be selected before creation. Advanced choices are shown only for an admin
pairing; a client pairing can still create with basic identity and model fields.
Android requires separate confirmation before
combining This computer with Auto approval. Full and Custom desktop grants
still require the computer's permission flow. Memory files, SKILL.md text,
and routine names and instructions can be edited in the creation draft.
The full-template editor also supports additional profile fields, skill
metadata and all schedule types. These edits apply only to the new bot, not to the saved
defaults. The server validates the complete template before creating it;
client-scoped pairings cannot submit template content.

An admin-scoped pairing can select and permanently delete individual messages
from a conversation, including older edited versions. The phone requires a
second confirmation and applies the server's deletion event to open chats.

Group settings include Dynamic response routing when paired with a server that
supports that mode. The setting is sent through the normal room PATCH endpoint;
older servers reject unsupported modes instead of silently changing behavior.

An admin-scoped pairing can open Prompt Inspector from a chat header. It shows
the paired computer's captured model input, full request, usage, diagnostics and
changes between compatible captures. Captures may include private conversations;
the screen loads them on demand, does not retain them in app storage, and offers
an explicit JSON export.

- `applicationId` — `com.openmausbot.companion`
- `minSdk` 26 (Android 8.0), `targetSdk` / `compileSdk` 37
- Deep-link scheme — `openmausbot`
- Two modules: `:core` (protocol, ported from `ios/Sources/CompanionCore`) and
  `:app` (Compose UI, Android platform)

For a bot's current thread, open its thread picker to see token usage reported
by the paired computer. Cached and uncached input are shown separately when
the provider reports a cache split; cost and context appear only when known.

Long-press a text message in a bot or non-DM room chat to pin it. The pinned
message stays above the transcript; tapping it loads older history when needed
and jumps to the message. Each conversation can have one pin.

In a group chat, tap the group avatar to edit its name, members, instructions,
and default responder. The paired computer validates and saves these changes;
an active turn can temporarily block changes to members or routing. Direct
messages have no group settings. Existing dynamic routing remains active if
you leave the responder unchanged.

Open a bot's settings to edit its standing instructions. Android enforces the
same 24,000-byte UTF-8 limit as the computer. When the computer reports an
external SOUL.md edit, resolve that difference on the computer first; the
phone does not silently overwrite it.

Bot settings can show profile change history from the paired computer. The
list omits full standing-instruction text; an eligible SOUL.md change can be
undone only after confirmation, using the server's revision check.
Bot settings can browse and edit the paired computer's MEMORY.md, topic files,
and daily logs. Saves include the hash of the version opened on the phone so
an intervening bot edit cannot be overwritten silently. Memory history can be
read and eligible changes reverted after confirmation.
Bot settings can list skills stored on the paired computer, read their full
SKILL.md text, disable or remove them, and import from GitHub. Imports remain
disabled until you read the skill and explicitly enable it on the phone.
Settings can show the paired computer's read-only workspace usage ledger for
this month, last month, or the last 30 days, grouped by bot, model, or day.

An admin-paired phone can set, test, discover models for, and remove the paired
computer's Mistral API key in Settings → Providers. The key is stored by the
computer, not in Android preferences. Key operations require a protected HTTPS
or Tailscale route; a plain LAN pairing cannot send them.
The same Providers screen also supports Anthropic, xAI and OpenAI-compatible
connections, including an optional custom API URL. All key operations check
the protected route before sending the request.
Settings → Engines lists the paired computer's engine status and model count.
With an admin pairing, Android can ask the computer to refresh an engine's
models or install an unavailable engine when that host supports installation.
An installation requires confirmation and never installs software on the phone.
Settings → Threads lets an admin-paired phone set the maximum simultaneous
tasks per bot and the optional size and age limits for thread event logs on the
paired computer. Empty optional limits keep the host's unbounded defaults.
The editor rechecks the current host values before saving so an intervening
desktop edit is not silently overwritten.
Settings → Activity shows the paired computer's admin changes and approval
history with who, category and date filters. An admin pairing can inspect
changed values and export the same filtered range as CSV through Android's
document picker. Activity remains on the host; a client-only pairing cannot
read it.
Where the paired computer reports the `budgets` feature, Settings → Usage
offers a monthly workspace spending limit and warning threshold. An admin
pairing can edit them after the phone rechecks that the host's values did not
change in another window. A blank amount disables the cap; unpriced work may
not count toward it. Without that host entitlement the control is hidden.
Where the paired computer reports the `billing` feature, an admin pairing
can also edit its currency and per-model prices for input, output and
optional cached input from Settings → Usage. A `default` model key covers
models without their own row. Android rechecks the host's price list before
saving; unrelated phone pairings and hosts without billing do not show it.
Settings → Computer → Local VM shows the paired computer's container runtime,
shared desktop and per-bot inventory. An admin pairing can change shared vs.
per-bot isolation and the 1–4 instance limit. The shared VM's image download,
creation, start, stop and removal are explicit, confirmed actions on the PC;
opening the screen never starts a container. In per-bot mode, an admin can
also stop or remove an existing managed bot VM after a separate confirmation;
the bot and its conversations remain. Runtime installation still uses the
desktop's setup flow.
Settings → Backups can create an encrypted workspace archive on the paired
computer and stream it into a document chosen with Android's file picker.
The password and file content require HTTPS or Tailscale; the phone does not
retain the password or buffer the full archive. A partial chosen document is
removed if the transfer fails. An admin can also select an `.ombbackup` file
on Android, stream it to the paired computer, validate it with the password,
and inspect the backup summary. Nothing is replaced until the user types
`REPLACE` and confirms a second dialog. A successful restore needs a desktop
restart. Import and restore require HTTPS or Tailscale; ordinary LAN pairing
cannot send a backup password or archive.

Settings → Teams also offers “Create bot in this team”. It opens the regular
model-first bot form with that team preselected; the new member appears on the
paired computer as well. Unsaved membership choices and a pending team name
stay in place while the creation form is open.

From the same team screen, an admin can appoint or remove its Chief of Staff.
Handing the role to another bot is confirmed before the server makes the
change. The appointment option requires a model engine that supports bot
coordination; the computer enforces the final role change.

Settings → Bot defaults lets an admin choose the provider, model and reasoning
effort inherited by future bots. Saving rereads the computer's effective
default; it does not change existing bots.
This server endpoint requires an admin-scoped pairing. Cost estimates and
unpriced turns are labeled; the phone does not alter budget settings.
If the paired workspace has an organization library, bot settings also list
skills the admin published for that bot. Adding one requires confirmation and
enables it immediately, matching the desktop's organization-skill behavior.
Bot settings show the computer destination and approval mode reported by the
paired computer for the current chat. Android can select the computer
destination for that chat through the server's task endpoint, or follow the
bot default again. A phone paired with the `admin` scope can also change the
bot-wide computer default; an ordinary `client` pairing cannot. Choosing This
computer while Auto approval is active requires a separate confirmation, and
the server still validates the change. Elevated approval levels remain
desktop-only. Admin-scoped phones can enter an absolute working-folder path
on the paired computer or return to the private bot folder. The server checks
that the path exists and is a directory; an existing task may retain its old
folder until a new task starts. The same admin pairing can switch a bot between
Ask and Auto approval. Auto on This computer has its own confirmation. Full
and Custom approval changes require the desktop app.

Bot settings also show the browser session selected on the paired computer.
An admin-paired phone can choose the bot's own browser, a temporary browser,
or an existing named profile. The server rejects a switch during a busy turn
or while browser control is held. Settings → Browser lets an admin-paired
phone create, rename and delete named profiles on the host. Whole-list saves
compare the previous list so another device's edits cannot be erased silently;
deleting a profile in use by a running bot is disabled.

## Build and test

Requires **JDK 17** and an Android SDK. Gradle arrives through the wrapper, so
there is nothing to install for it.

```sh
cd android
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

CI also builds the preview APK described below (`.github/workflows/ci.yml`).
Gradle caches aggressively — a suspiciously fast `BUILD SUCCESSFUL` usually means nothing ran.
Prefix `cleanTest` when a test count matters:

```sh
./gradlew cleanTest :core:test :app:testDebugUnitTest :app:assembleDebug
```

## Installable threads preview

```sh
cd android
./gradlew :app:assemblePreview
```

Install `app/build/outputs/apk/preview/app-preview.apk`. Its launcher name is
**OpenMausBot Preview**, its application ID is `com.openmausbot.companion.preview`,
and its version ends in `-threads-preview`. Gradle signs it with the local debug
key, so no release signing material is needed. It installs beside the released
app with separate pairing, preferences and messages; it does not update that app.

Open Preview and pair using its QR scanner or manual address form. Preview does
not register the release's `openmausbot://` pairing links or system share targets.
Its file-sharing provider uses the preview application ID too.

Pull-request CI runs the core and debug UI unit tests, builds both APK variants,
and uploads `android-threads-preview-<tested-commit>-<attempt>` for 14 days. Download
and extract that run's artifact to get the same installable preview APK. Debug
keys can differ between local builds and CI runners; Android requires the same
key to update an existing preview installation. A fresh installation after
removing a differently signed preview loses only that preview's local data.

Verify conversation changes against an isolated fixture as described in
[`docs/verification/README.md`](../docs/verification/README.md), using a disposable
emulator. These builds do not exercise live pairing or a physical phone by
themselves.

## Building a release APK

```sh
cd android
./gradlew :app:assembleRelease
```

With no signing material configured this writes an **unsigned** APK:

```
app/build/outputs/apk/release/app-release-unsigned.apk
```

That is a supported outcome, not a degraded one — it is the artifact to hand to
whoever holds the release key. The filename says which of the two happened: a
signed build writes `app-release.apk` instead.

The APK is already zip-aligned by the Android Gradle Plugin, and `apksigner`
preserves that alignment. **Do not run `zipalign` or `jarsigner`.**

## Signing it

The signing key belongs to the maintainer and never enters this repository.
`.gitignore` refuses `*.jks`, `*.keystore` and `*.p12` repo-wide, and
`android/keystore.properties` alongside them.

Create a key once, outside any clone of this repository:

```sh
keytool -genkeypair -v -keystore ~/openmausbot-release.jks \
  -storetype PKCS12 -alias openmausbot -keyalg RSA -keysize 4096 -validity 10000
```

Then, for each release:

```sh
# whichever build-tools version is installed; any recent one works
APKSIGNER="$(ls -d "$ANDROID_HOME"/build-tools/* | tail -1)/apksigner"

"$APKSIGNER" sign --ks ~/openmausbot-release.jks --ks-key-alias openmausbot \
  --out app-release.apk app-release-unsigned.apk

"$APKSIGNER" verify --verbose --print-certs app-release.apk
```

Without `--ks-pass`, `apksigner` prompts for the password, which keeps it out of
the shell history. `verify` should report `Verifies` along with v2 and v3
signature scheme lines.

Keep that keystore file and its passwords backed up. Google Play ties an app to
the key that first signed it; losing it means the listing cannot be updated.

### Signing from Gradle instead

To have `assembleRelease` produce a signed APK directly, supply the key material
one of two ways. The environment wins over the file, so a CI runner cannot
silently inherit a stale `keystore.properties` from a cached workspace.

`android/keystore.properties` (gitignored):

```properties
storeFile=/absolute/path/to/openmausbot-release.jks
storePassword=…
keyAlias=openmausbot
keyPassword=…
```

or the environment, for CI secrets:

```
OPENMAUSBOT_KEYSTORE_FILE
OPENMAUSBOT_KEYSTORE_PASSWORD
OPENMAUSBOT_KEY_ALIAS
OPENMAUSBOT_KEY_PASSWORD   # optional; PKCS12 reuses the store password
```

Supply all of it or none of it. A build handed only part of the material stops
and names what is missing, rather than quietly producing an unsigned APK that
looks finished and cannot be published.

Both paths declare v2 and v3 signature schemes. v3 is what makes the key
rotatable later: without a v3 block there is no signing lineage for a new key to
prove it descends from the old one. v1 stays off — it is JAR signing, for
Android 6 and below, which `minSdk 26` already excludes.

## Versioning

There is one line to edit per release, in `app/build.gradle.kts`:

```kotlin
private val appVersionName = "1.0.0"
```

`versionCode` is derived from it as `MAJOR * 10000 + MINOR * 100 + PATCH`, so
`1.0.0` is `10000`, `1.0.1` is `10001`, `2.0.0` is `20000`. Each part must be in
`0..99`; a name outside `X.Y.Z` fails the build rather than resolving to a number
nobody chose. There is no second number to remember to bump.

Play orders uploads by `versionCode` alone and refuses one it has already
accepted, so the code has to rise whenever the name does.

## Why R8 is off

`isMinifyEnabled = false` is a decision, not an oversight, and
`app/build.gradle.kts` carries the full reasoning.

The short version: `:core` has 66 `@Serializable` classes, and
kotlinx.serialization reaches their generated serializers reflectively, by name,
at the moment a frame is decoded. R8 sees no call site for those companions and
strips or renames them. The result builds, installs and opens — then fails the
first time the phone talks to the computer, in release only, as a
`SerializationException` with a stack trace made of one-letter class names.

Turning it on means writing keep rules *and testing them* against a real pairing
and a real session, because no unit test reaches that failure mode and a
minified APK that launches proves nothing. Until someone does that work, `false`
is the tested state, and roughly 34 MB is its price.
