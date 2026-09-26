# Desktop transcription

These checks use disposable settings and synthetic audio, never the user's
transcription credentials, chats, or microphone.

## Automated checks

```sh
node --test electron/stt-engine-selection.node-test.mjs electron/stt-core.node-test.mjs electron/stt-settings.node-test.mjs electron/stt-local.node-test.mjs electron/preload.node-test.mjs electron/capabilities.node-test.mjs
pnpm exec vitest run src/lib/transcription.test.ts src/lib/desktop.test.ts
```

The tests cover provider defaults, removed-provider migration, blank-key
preservation and explicit removal, locked-storage failures, atomic save/reload,
provider request formats, sanitized HTTP errors, bounded audio, window-scoped
cancellation, remote-page isolation, and verified downloads. Segment tests
check silence, ordering, final flush, cancellation, errors, and resampling.

## Optional real Electron smoke

Set `OMB_VERIFY_OUTPUT` to a new evidence directory, then run:

```sh
pnpm exec electron scripts/testing/transcription-ui-smoke.mjs
```

This mounts the actual settings component and recording hook with the production
preload and transcription IPC service. Electron uses a temporary user-data path,
its real protected storage, Chromium's fake microphone, and a loopback
OpenAI-compatible endpoint. It verifies saved provider/key state after renderer
reload and fresh-store decryption; constant dialog height and initial focus;
nonzero audio levels; Stop flushing into an editable draft; and chat-switch
cancellation. The fixture closes only its own window, server and preview.
It saves `report.json` and an English settings screenshot.

This does not measure recognition accuracy or prove any paid provider account,
physical microphone, macOS Speech helper, Windows Voice Typing panel, downloaded
Whisper runtime, or packaged OS permission prompt. Those require platform checks.

## Platform checks before release

- Windows: default Win+H opens over the focused composer; stop from its system
  panel. A chosen cloud/local provider persists after restarting the app.
- macOS: Apple Speech starts/stops and permission denial is recoverable;
  cloud/local providers show live levels and leave editable text.
- Linux: choose/download a supported local runtime/model; verify speech and
  CPU settings, then verify a configured cloud provider.
- Record into private and group drafts; switch chats during capture and an
  in-flight segment. No transcript may arrive in the new chat or send itself.
- Check approved cloud endpoints with consented audio; no API key should appear
  in renderer state, errors, screenshots, or ordinary logs.

Custom executable paths require a main-process native picker grant owned by the
selecting window. Ordinary saves can retain the persisted selection or clear
it, but cannot substitute an arbitrary renderer-supplied program. Node tests
cover cross-window, replaced and destroyed-window grants; the real Electron
fixture verifies both save and install IPC reject an unselected executable.

## Recording and completion preferences

Settings → Transcription provides **End recording** (manual or continuous silence), a positive pause length in milliseconds, and **After transcription** (insert draft or send). Defaults are manual, 1000 ms and insert. The selected configuration is frozen for each recording. System Windows Win+H owns its capture and insertion behavior; these app controls apply to cloud/local providers and are visibly unavailable for Win+H. Save displays pending then a persistent checkmark until another edit.

The isolated Electron smoke additionally verifies final auto-send happens once and chat switching never sends. Synthetic PCM tests cover initial silence, accumulated speech, pause reset and manual mode. Auto-send is suppressed on errors, cancellation, empty results and stale chat sessions; attachments still being prepared leave the text and a visible notice.
