import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { callSpeech, windowsCallSpeechAvailable } from "./call-speech";

describe("Windows call transcription", () => {
  let processor: { onaudioprocess?: (event: unknown) => void; connect: () => void; disconnect: () => void };
  const stopTrack = vi.fn(), cancel = vi.fn(async () => {}), transcribe = vi.fn(async (_request: { id: string; pcm: ArrayBuffer }) => ({ text: "Hallo" }));
  beforeEach(() => {
    vi.clearAllMocks();
    processor = { connect: vi.fn(), disconnect: vi.fn() };
    vi.stubGlobal("window", { ogb: { platform: "win32", sttBegin: vi.fn(async () => ({ id: "test", kind: "audio" })), sttCancel: cancel, sttTranscribe: transcribe } });
    vi.stubGlobal("navigator", { mediaDevices: { getUserMedia: vi.fn(async () => ({ getTracks: () => [{ stop: stopTrack }] })) } });
    vi.stubGlobal("AudioContext", class {
      state = "running"; destination = {};
      async resume() {} async close() { this.state = "closed"; }
      createMediaStreamSource() { return { connect: vi.fn(), disconnect: vi.fn() }; }
      createScriptProcessor() { return processor; }
    });
  });
  afterEach(async () => { await callSpeech()?.speechStop(); vi.unstubAllGlobals(); });
  it("sends microphone PCM to the configured engine and releases the microphone before transcription", async () => {
    const bridge = callSpeech()!, transcript = vi.fn(), ended = vi.fn();
    const off = bridge.onSpeechTranscript(transcript), offEnd = bridge.onSpeechEnd(ended);
    await bridge.speechStart();
    processor.onaudioprocess?.({ inputBuffer: { sampleRate: 48000, getChannelData: () => new Float32Array([0.5, 0.1, 0.2, -0.5, 0, 0]) } });
    await bridge.speechFinish?.();
    expect(stopTrack).toHaveBeenCalled();
    const pcm = new Int16Array(transcribe.mock.calls[0]![0].pcm);
    expect([...pcm]).toEqual([16384, -16383]);
    expect(transcript).toHaveBeenCalledWith({ text: "Hallo", partial: false });
    expect(ended).toHaveBeenCalledWith({ code: 0 });
    off(); offEnd();
  });
  it("rejects Windows voice typing because it does not return a call transcript", async () => {
    window.ogb!.sttBegin = vi.fn(async () => ({ id: "native", kind: "system" } as never));
    await expect(callSpeech()!.speechStart()).rejects.toThrow();
    expect(navigator.mediaDevices.getUserMedia).not.toHaveBeenCalled();
    expect(cancel).toHaveBeenCalledWith("native");
  });
  it("does not emit a late transcript after hang-up", async () => {
    let resolve!: (result: { text: string }) => void;
    transcribe.mockImplementationOnce(() => new Promise(done => { resolve = done; }));
    const bridge = callSpeech()!, heard = vi.fn(), off = bridge.onSpeechTranscript(heard);
    await bridge.speechStart();
    processor.onaudioprocess?.({ inputBuffer: { sampleRate: 16000, getChannelData: () => new Float32Array([0.3]) } });
    const finishing = bridge.speechFinish?.();
    await bridge.speechStop(); resolve({ text: "stale" }); await finishing;
    expect(heard).not.toHaveBeenCalled(); off();
    window.ogb!.remoteClient = { active: true } as never;
    expect(windowsCallSpeechAvailable()).toBe(false);
  });
});
