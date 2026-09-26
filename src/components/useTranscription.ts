import { useEffect, useRef, useState } from "react";
import { createSpeechSegments, createSilenceStop, transcriptionError } from "@/lib/transcription";

type Session = {
  id?: string; native: boolean; text: string; prefix: string; chatKey: string;
  segments?: ReturnType<typeof createSpeechSegments>;
  stream?: MediaStream; context?: AudioContext; processor?: ScriptProcessorNode;
  source?: MediaStreamAudioSourceNode; analyser?: AnalyserNode; monitor?: GainNode;
  frame?: number; timeout?: ReturnType<typeof setTimeout>; off: Array<() => void>;
  afterAction?: "insert" | "send"; finishing?: boolean;
};

export function useTranscription(chatKey: string, text: string, edit: (text: string) => void, send?: (text: string) => void) {
  const [phase, setPhase] = useState<"idle" | "starting" | "recording" | "processing">("idle");
  const [error, setError] = useState<string | null>(null), [levels, setLevels] = useState<number[]>([0, 0, 0, 0, 0]);
  const [seconds, setSeconds] = useState(0);
  const current = useRef<Session | null>(null), revision = useRef(0), editRef = useRef(edit);
  editRef.current = edit;
  const sendRef = useRef(send); sendRef.current = send;
  const chatRef = useRef(chatKey); chatRef.current = chatKey;
  const closeAudio = (s: Session) => {
    if (s.frame !== undefined) cancelAnimationFrame(s.frame);
    s.processor?.disconnect(); s.source?.disconnect(); s.analyser?.disconnect(); s.monitor?.disconnect();
    s.stream?.getTracks().forEach(track => track.stop());
    if (s.context && s.context.state !== "closed") void s.context.close().catch(() => {});
  };
  const release = (s: Session) => {
    closeAudio(s); clearTimeout(s.timeout); s.off.forEach(fn => fn());
    s.segments?.cancel();
    if (s.id) void window.ogb?.sttCancel?.(s.id).catch(() => {});
  };
  const cancel = () => {
    revision.current++;
    const s = current.current; current.current = null;
    if (s) { release(s); if (s.native) void window.ogb?.speechStop(); }
    setPhase("idle"); setLevels([0, 0, 0, 0, 0]);
  };
  const finish = (s: Session, message?: string, completed = false) => {
    if (current.current !== s) return;
    current.current = null; release(s); setPhase("idle"); setLevels([0, 0, 0, 0, 0]);
    if (message) setError(message);
    if (completed && !message && s.text.trim() && s.afterAction === "send" && s.chatKey === chatRef.current) sendRef.current?.(s.prefix ? `${s.prefix} ${s.text}` : s.text);
  };
  useEffect(() => {
    cancel(); setError(null);
    return cancel;
  }, [chatKey]);
  useEffect(() => {
    if (phase !== "recording") return;
    setSeconds(0);
    const timer = setInterval(() => setSeconds(n => n + 1), 1000);
    return () => clearInterval(timer);
  }, [phase]);
  const stop = async () => {
    const s = current.current;
    if (!s || s.finishing) return;
    s.finishing = true;
    closeAudio(s); setPhase("processing");
    if (s.native) {
      s.timeout = setTimeout(() => { if (current.current === s) { void window.ogb?.speechStop(); finish(s, "Transcription timed out."); } }, 15_000);
      try { await window.ogb?.speechFinish?.(); } catch (e) { finish(s, transcriptionError(e)); }
    } else {
      const result = await s.segments?.finish();
      finish(s, result ? undefined : "No speech was recognized.", true);
    }
  };
  const start = async (focusInput: () => void) => {
    if (phase !== "idle" || current.current) return;
    const bridge = window.ogb;
    if (!bridge) return;
    const generation = ++revision.current;
    setPhase("starting"); setError(null);
    let s: Session | undefined;
    try {
      const choice = bridge.sttBegin ? await bridge.sttBegin() : { kind: "native", id: undefined };
      if (generation !== revision.current) { if (choice.id) void bridge.sttCancel?.(choice.id); return; }
      if (choice.kind === "system") {
        try { focusInput(); await bridge.sttVoiceTyping!(); }
        finally { if (choice.id) await bridge.sttCancel?.(choice.id); if (generation === revision.current) setPhase("idle"); }
        return;
      }
      s = { id: choice.id, native: choice.kind === "native", text: "", prefix: text.trim(), chatKey, off: [], afterAction: "afterAction" in choice ? choice.afterAction : "insert" };
      const session = s; current.current = session;
      const put = (value: string) => {
        if (current.current !== session || session.chatKey !== chatRef.current) return;
        session.text = value; editRef.current(session.prefix ? `${session.prefix} ${value}` : value);
      };
      if (!session.native) session.segments = createSpeechSegments({
        transcribe: async pcm => (await bridge.sttTranscribe!({ id: session.id!, pcm })).text,
        onText: put, onError: message => finish(session, message),
      });
      session.stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true } });
      if (current.current !== session) { closeAudio(session); return; }
      session.context = new AudioContext(); await session.context.resume();
      if (current.current !== session) { closeAudio(session); return; }
      session.source = session.context.createMediaStreamSource(session.stream);
      session.analyser = session.context.createAnalyser(); session.analyser.fftSize = 256;
      session.source.connect(session.analyser);
      session.monitor = session.context.createGain(); session.monitor.gain.value = 0;
      session.analyser.connect(session.monitor); session.monitor.connect(session.context.destination);
      const bins = new Uint8Array(session.analyser.frequencyBinCount);
      const draw = () => {
        if (current.current !== session || session.context?.state === "closed") return;
        session.analyser!.getByteFrequencyData(bins);
        setLevels(Array.from({ length: 5 }, (_, i) => {
          const band = bins.subarray(i * 10, i * 10 + 10);
          return band.reduce((sum, value) => sum + value, 0) / (band.length * 255);
        }));
        session.frame = requestAnimationFrame(draw);
      };
      draw();
      const pause = createSilenceStop("stopMode" in choice ? choice.stopMode : "manual", "silenceMs" in choice ? choice.silenceMs : 1000, () => void stop());
      session.processor = session.context.createScriptProcessor(4096, 1, 1);
      session.processor.onaudioprocess = event => {
        const input = event.inputBuffer.getChannelData(0), rate = event.inputBuffer.sampleRate;
        session.segments?.push(input, rate); pause(input, rate);
      };
      session.source.connect(session.processor); session.processor.connect(session.context.destination);
      if (session.native) {
        session.off.push(bridge.onSpeechTranscript(line => { if (typeof line.text === "string") put(line.text); }));
        session.off.push(bridge.onSpeechEnd(info => finish(session, info.code ? "Speech recognition failed. Check microphone and speech permissions." : undefined, session.finishing === true)));
        await bridge.speechStart();
      }
      if (current.current === session) setPhase("recording");
    } catch (e) {
      if (s && current.current === s) finish(s, transcriptionError(e));
      else if (generation === revision.current) { setPhase("idle"); setError(transcriptionError(e)); }
    }
  };
  return { phase, error, levels, seconds, cancel, toggle: (focusInput: () => void) => phase === "recording" ? stop() : start(focusInput) };
}
