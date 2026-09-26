# Android speech input and desktop credential import

Use disposable profiles and synthetic credentials; never export the user's real keys for a test.

## Checks

```sh
node --test electron/stt-import.node-test.mjs
pnpm exec vitest run server/routes/stt-import.test.ts companion/test/proxy-response.test.ts companion/test/custom-permissions.test.ts companion/test/pairing-access.test.ts
./android/gradlew -p android :app:testDebugUnitTest --tests '*SpeechDictationTest' --tests '*SttCloudTest' --tests '*SttSettingsFeedbackTest' --tests '*MobileChatPolishTest' :core:test --tests '*SttImportClientTest'
pnpm i18n:android:check
```

The HTTP tests verify provider authorization, revocation while awaiting approval, and the longer approval deadline. Desktop tests verify approval happens before loading keys, STT-only filtering, expiry, and hybrid encryption. Android tests decrypt a Node-generated synthetic import fixture and reject changed authenticated metadata; mock servers verify provider requests and error responses. Compose tests exercise pending, saved, imported, edited, declined, and retry states at 360 dp in German. Dictation tests cover manual finalization, readiness, error details, stale callbacks, and lifecycle cancellation.

## User workflow

Android Settings → Spracheingabe → STT-Anbieter einstellen (also available beside the chat microphone). Choose Android, OpenRouter, OpenAI, Groq, Deepgram, Azure Speech, or an OpenAI-compatible service. Tap **Vom Desktop übernehmen**, then **Übertragen** in the native desktop confirmation. This copies configured STT profiles, model, endpoint and language; encrypted preferences retain them on Android. The paired device needs the existing provider-management permission. Declining, timeout, absent credentials, revoked access, offline desktop, invalid import, and failed save leave visible errors and enable retry. After import, provider requests go directly from Android.

Tap the microphone once to start; tap again to finish and await transcription. Starting, recording and processing have separate visible states. Leaving the chat or tapping during processing cancels. Native Android recognition uses `stopListening` for finalization, and client failures are visible. Cloud capture is bounded to 60 seconds per recording; microphone input stays in memory.

Explanations are behind the question-mark button. Save/import buttons show pending status and a persistent checkmarked success until another edit.

## Limits of fixture verification

Mocks prove transport and parsing, not current account credit, actual provider availability or a particular phone microphone. A physical-device recording and a paid-provider call remain separate checks.

Native Android recognition delegates audio-focus ownership to the system recognition service; cloud recording continues to use the app's interruption gate. On the Galaxy A04e (Android 14), logs from the previous APK show Google requesting exclusive focus followed within milliseconds by the app cancelling and destroying its recognizer. The regression test covers both native engine variants through readiness, partial results, manual finalization and final text without requesting competing client focus. Existing interruption tests still cover app-owned recording.

## Primary provider documentation

- https://developers.openai.com/api/docs/guides/speech-to-text
- https://openrouter.ai/blog/tutorials/transcription-on-openrouter/
- https://console.groq.com/docs/speech-to-text
- https://developers.deepgram.com/reference/speech-to-text/listen-pre-recorded
- https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-speech-to-text-short

On this Windows runner, the full core suite reports two existing network-fixture failures (`ConnectionTest.zonedIpv6UsesScopedAddressOnTheRealOkHttpConnectPath` and `ServerSessionPairingTest.redirectedDescriptorIsRejectedWithoutFollowingIt`). Both were reproduced with the Client and Session sources from the preceding commit, before the STT additions; the dedicated import tests pass.
