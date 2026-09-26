import { useState } from "react";
import { setLocale } from "../../src/lib/i18n";
import { createRoot } from "react-dom/client";
import { TranscriptionSettings } from "../../src/components/TranscriptionSettings";
import { useTranscription } from "../../src/components/useTranscription";
import "../../src/styles.css";
setLocale("en");

function Preview() {
  const [text, setText] = useState(""), [chat, setChat] = useState("first");
  const [sent, setSent] = useState<string[]>([]);
  const speech = useTranscription(chat, text, setText, value => { setSent(items => [...items, value]); setText(""); });
  return <main className="min-h-screen bg-app p-12 text-ink">
    <h1 className="mb-4 text-xl">Transcription fixture</h1>
    <textarea aria-label="Draft" value={text} onChange={e => setText(e.target.value)} className="w-full rounded-xl bg-panel p-4" />
    <div className="mt-4 flex items-center gap-3">
      <button onClick={() => void speech.toggle(() => document.querySelector("textarea")?.focus())}>{speech.phase === "recording" ? "Stop recording" : "Start recording"}</button>
      <TranscriptionSettings disabled={speech.phase !== "idle"} />
      <span data-phase={speech.phase}>{speech.phase}</span>
      <span data-levels={speech.levels.join(",")}>{speech.seconds}s</span>
      <button onClick={() => { speech.cancel(); setChat(chat === "first" ? "second" : "first"); setText(""); }}>Switch chat</button>
    </div>
    <p role="alert">{speech.error}</p>
    <output data-sent={sent.length}>{sent.join(" | ")}</output>
  </main>;
}
createRoot(document.getElementById("root")!).render(<Preview />);
