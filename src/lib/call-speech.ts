import { createSilenceStop } from "./transcription";
import { t } from "./i18n";

type Transcript = { text?: string; partial?: boolean; error?: string };
type End = { code: number | null; reason?: string };
type Capture = { generation: number; id: string; stream?: MediaStream; context?: AudioContext;
  source?: MediaStreamAudioSourceNode; processor?: ScriptProcessorNode; chunks: Int16Array[];
  samples: number; offset: number; timer?: ReturnType<typeof setTimeout>; finishing?: boolean };
const transcripts = new Set<(line: Transcript) => void>(), ends = new Set<(info: End) => void>();
let generation = 0, capture: Capture | null = null;
function closeAudio(s: Capture) {
  clearTimeout(s.timer); s.processor?.disconnect(); s.source?.disconnect();
  s.stream?.getTracks().forEach(track => track.stop());
  if (s.context && s.context.state !== "closed") void s.context.close().catch(() => {});
}
function cancel() {
  generation++;
  const previous = capture; capture = null;
  if (previous) { closeAudio(previous); void window.ogb?.sttCancel?.(previous.id).catch(() => {}); }
}
async function finish() {
  const s = capture;
  if (!s || s.finishing) return;
  s.finishing = true; closeAudio(s);
  try {
    const pcm = new Int16Array(s.samples); let offset = 0;
    for (const chunk of s.chunks) { pcm.set(chunk, offset); offset += chunk.length; }
    const result = s.samples ? await window.ogb!.sttTranscribe!({ id: s.id, pcm: pcm.buffer }) : { text: "" };
    if (capture !== s || generation !== s.generation) return;
    capture = null;
    transcripts.forEach(cb => cb({ text: result.text, partial: false }));
    ends.forEach(cb => cb({ code: 0 }));
  } catch (error) {
    if (capture !== s) return;
    capture = null;
    transcripts.forEach(cb => cb({ error: error instanceof Error ? error.message : String(error) }));
    ends.forEach(cb => cb({ code: 1, reason: "transcription-failed" }));
  } finally { void window.ogb?.sttCancel?.(s.id).catch(() => {}); }
}
async function start(options?: { endpointMs?: number }) {
  cancel(); const mine = generation;
  const bridge = window.ogb;
  if (!bridge?.sttBegin || !bridge.sttTranscribe) throw Error(t("call.stt.unavailable"));
  const choice = await bridge.sttBegin();
  if (generation !== mine) { await bridge.sttCancel?.(choice.id); return; }
  if (choice.kind !== "audio") {
    await bridge.sttCancel?.(choice.id);
    throw Error(t("call.stt.setup"));
  }
  const s: Capture = { generation: mine, id: choice.id, chunks: [], samples: 0, offset: 0 };
  capture = s;
  try {
    s.stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true } });
    if (capture !== s) { closeAudio(s); return; }
    s.context = new AudioContext(); await s.context.resume();
    if (capture !== s) { closeAudio(s); return; }
    s.source = s.context.createMediaStreamSource(s.stream);
    const pause = createSilenceStop(options?.endpointMs === undefined ? "manual" : "silence", options?.endpointMs ?? 850, () => void finish());
    s.processor = s.context.createScriptProcessor(4096, 1, 1);
    s.processor.onaudioprocess = event => {
      if (capture !== s || s.finishing) return;
      const input = event.inputBuffer.getChannelData(0), rate = event.inputBuffer.sampleRate, values: number[] = [];
      for (; s.offset < input.length; s.offset += rate / 16000) values.push(Math.round(Math.max(-1, Math.min(1, input[Math.floor(s.offset)]!)) * 32767));
      s.offset -= input.length;
      s.chunks.push(new Int16Array(values)); s.samples += values.length;
      pause(input, rate);
      if (s.samples >= 16000 * 60) void finish();
    };
    s.source.connect(s.processor); s.processor.connect(s.context.destination);
    s.timer = setTimeout(() => void finish(), 60_000);
  } catch (error) { if (capture === s) cancel(); throw error; }
}
/** Calls use the same configured STT engine as dictation on Windows. macOS retains native speech. */
export function callSpeech() {
  const bridge = window.ogb;
  if (bridge?.platform !== "win32") return bridge;
  return {
    speechStart: start, speechStop: async () => cancel(), speechFinish: finish,
    onSpeechTranscript(cb: (line: Transcript) => void) { transcripts.add(cb); return () => { transcripts.delete(cb); }; },
    onSpeechEnd(cb: (info: End) => void) { ends.add(cb); return () => { ends.delete(cb); }; },
  };
}
export function windowsCallSpeechAvailable(): boolean {
  return window.ogb?.platform === "win32" && Boolean(window.ogb.sttBegin && window.ogb.sttTranscribe) && !window.ogb.remoteClient?.active;
}
