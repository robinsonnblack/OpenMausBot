import { t } from "@/lib/i18n";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { ChevronDown, CircleHelp, X } from "lucide-react";
import { transcriptionError, type SttConfig, type SttState } from "@/lib/transcription";
import { activeLocale } from "@/lib/i18n";

function Help({ label, children }: { label: string; children: ReactNode }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    if (!open) return;
    const close = (e: PointerEvent) => { if (!ref.current?.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("pointerdown", close);
    return () => document.removeEventListener("pointerdown", close);
  }, [open]);
  return <span ref={ref} className="relative inline-flex" onKeyDown={e => {
    if (e.key === "Escape" && open) { e.preventDefault(); e.stopPropagation(); setOpen(false); }
  }}>
    <button type="button" aria-label={`About ${label}`} aria-expanded={open} onClick={() => setOpen(!open)}
      className="rounded p-1 text-ink-secondary hover:text-ink focus-visible:ring-2 focus-visible:ring-accent"><CircleHelp size={14} /></button>
    {open && <span role="group" aria-label={`${label} help`} className="absolute left-0 top-full z-30 w-64 rounded-xl border border-hairline bg-panel p-3 text-xs font-normal text-ink-secondary shadow-xl">{children}</span>}
  </span>;
}
const inputClass = "w-full rounded-lg border border-hairline/50 bg-inset px-3 py-2 text-[13px] text-ink outline-none focus:border-accent";
const languages = [["auto", "Automatic / system default"], ["en-US", "English (US)"], ["en-GB", "English (UK)"], ["de-DE", "German"], ["fr-FR", "French"], ["es-ES", "Spanish"], ["it-IT", "Italian"], ["pt-BR", "Portuguese"], ["ja-JP", "Japanese"], ["zh-CN", "Chinese"], ["ko-KR", "Korean"], ["nl-NL", "Dutch"], ["pl-PL", "Polish"], ["ru-RU", "Russian"]];

export function TranscriptionSettings({ disabled = false }: { disabled?: boolean }) {
  const [open, setOpen] = useState(false), [data, setData] = useState<SttState | null>(null);
  const [cfg, setCfg] = useState<SttConfig | null>(null), [error, setError] = useState("");
  const [busy, setBusy] = useState(false), [downloading, setDownloading] = useState(false);
  const [saved, setSaved] = useState(false);
  const de = activeLocale().startsWith("de");
  const panel = useRef<HTMLElement>(null), workBusy = useRef(false);
  workBusy.current = busy || downloading;
  const bridge = window.ogb;
  useEffect(() => {
    if (!open || !bridge?.sttSettings) return;
    let alive = true;
    void bridge.sttSettings().then(result => { if (alive) { setData(result); setCfg(result.config); } }).catch(e => { if (alive) setError(transcriptionError(e)); });
    const previous = document.activeElement as HTMLElement | null;
    panel.current?.focus();
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        if (panel.current?.querySelector('[role="group"]')) return;
        e.preventDefault(); e.stopPropagation(); if (!workBusy.current) setOpen(false);
      }
      if (e.key === "Tab") {
        const nodes = panel.current?.querySelectorAll<HTMLElement>("button:not(:disabled),input:not(:disabled),select:not(:disabled)");
        if (!nodes?.length) { e.preventDefault(); return; }
        const first = nodes[0]!, last = nodes[nodes.length - 1]!;
        if (e.shiftKey && (document.activeElement === first || document.activeElement === panel.current)) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
    };
    document.addEventListener("keydown", key, true);
    return () => { alive = false; document.removeEventListener("keydown", key, true); previous?.focus(); };
  }, [open, bridge]);
  useEffect(() => {
    if (!downloading) return;
    const timer = setInterval(() => { void bridge?.sttSettings?.().then(setData).catch(() => {}); }, 700);
    return () => clearInterval(timer);
  }, [downloading, bridge]);
  if (!bridge?.sttSettings) return null;
  const update = (patch: Partial<SttConfig>) => { setSaved(false); setCfg(c => c && ({ ...c, ...patch })); };
  const provider = data?.providers.find(p => p.id === cfg?.provider);
  const profile = cfg?.profiles[cfg.provider];
  const setProfile = (patch: Record<string, unknown>) => { setSaved(false); setCfg(c => c && ({ ...c, profiles: { ...c.profiles, [c.provider]: { ...c.profiles[c.provider]!, ...patch } } })); };
  const field = (label: string, node: ReactNode, help?: string, status?: ReactNode) => <div className="space-y-1.5">
    <div className="flex items-center gap-1 text-[13px] text-ink-secondary">{label}{help && <Help label={label}>{help}</Help>}{status}</div>{node}</div>;
  const install = async () => {
    if (!cfg) return;
    setDownloading(true); setError("");
    try { const local = await bridge.sttInstall!({ id: cfg.localModel, executable: cfg.executable }); setData(d => d && ({ ...d, local })); }
    catch (e) { setError(transcriptionError(e)); } finally { setDownloading(false); }
  };
  return <>
    <button type="button" disabled={disabled} aria-label={t("hardcoded.components.TranscriptionSettings.3cd97c3d")} title={t("hardcoded.components.TranscriptionSettings.3cd97c3d")}
      className="flex h-8 w-5 shrink-0 items-center justify-center rounded-full text-ink-secondary hover:bg-raised disabled:opacity-40"
      onClick={() => { setError(""); setSaved(false); setData(null); setCfg(null); setOpen(true); }}><ChevronDown size={12} /></button>
    {open && createPortal(<div className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/50 p-4">
      <section ref={panel} tabIndex={-1} role="dialog" aria-modal="true" aria-label={t("hardcoded.components.TranscriptionSettings.3cd97c3d")}
        className="flex w-full max-w-[520px] flex-col rounded-3xl border border-hairline/50 bg-panel p-5 shadow-xl outline-none" style={{ height: "min(680px, calc(100dvh - 32px))" }}>
        <div className="mb-4 flex shrink-0 items-center justify-between">
          <div className="flex items-center gap-2"><h2 className="text-lg font-semibold">{t("hardcoded.components.TranscriptionSettings.1a255c75")}</h2><Help label={t("hardcoded.components.TranscriptionSettings.ad591e37")}>{t("hardcoded.components.TranscriptionSettings.85c16acf")}</Help></div>
          <button type="button" aria-label={t("hardcoded.components.TranscriptionSettings.c4efe52a")} disabled={busy || downloading} onClick={() => setOpen(false)} className="rounded-lg p-2 text-ink-secondary hover:bg-raised"><X size={18} /></button>
        </div>
        {!cfg || !data ? <p className="text-sm text-ink-secondary">{error || "Loading…"}</p> : <form className="flex min-h-0 flex-1 flex-col" onSubmit={async e => {
          e.preventDefault(); setBusy(true); setError("");
          try { const result = await bridge.sttSave!(cfg); setCfg(result.config); setData(result); setSaved(true); } catch (err) { setError(transcriptionError(err)); } finally { setBusy(false); }
        }}>
          <div className="min-h-0 flex-1 space-y-4 overflow-y-auto" style={{ scrollbarGutter: "stable" }}>
            {field("Provider", <select aria-label={t("hardcoded.components.TranscriptionSettings.10130318")} className={inputClass} value={cfg.provider} disabled={busy || downloading} onChange={e => update({ provider: e.target.value })}>{data.providers.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select>, provider?.help)}
            {provider?.kind === "system" ? <div className="flex items-center gap-2 text-sm text-ink-secondary"><span>{de ? "Win+H steuert Aufnahme und Texteingabe selbst" : "Win+H controls recording and text input itself"}</span><Help label="Win+H">{de ? "Für die folgenden Aufnahmeoptionen einen Anbieter wählen, der direkt in OpenMausBot aufnimmt, etwa OpenRouter oder lokales Whisper." : "Choose an in-app recording provider, such as OpenRouter or Local Whisper, to use recording and send options."}</Help></div> : <>
              {field(de ? "Aufnahme beenden" : "End recording", <select aria-label="End recording" className={inputClass} disabled={busy} value={cfg.stopMode} onChange={e => update({ stopMode: e.target.value as SttConfig["stopMode"] })}>
                <option value="manual">{de ? "Nur durch erneutes Tippen" : "Only by tapping again"}</option><option value="silence">{de ? "Automatisch nach einer Sprechpause" : "Automatically after a pause"}</option>
              </select>, de ? "Sprechpausen beenden die Aufnahme nur im automatischen Modus. Erneutes Tippen beendet sie in beiden Modi." : "Pauses end recording only in automatic mode. Tapping again ends either mode.")}
              {cfg.stopMode === "silence" && field(de ? "Pausenlänge (Millisekunden)" : "Pause length (milliseconds)", <input aria-label="Pause length (milliseconds)" className={inputClass} type="number" min={1} step={1} disabled={busy} value={cfg.silenceMs || ""} onChange={e => update({ silenceMs: Number(e.target.value) })} />)}
              {field(de ? "Nach der Transkription" : "After transcription", <select aria-label="After transcription" className={inputClass} disabled={busy} value={cfg.afterAction} onChange={e => update({ afterAction: e.target.value as SttConfig["afterAction"] })}>
                <option value="insert">{de ? "Text nur einfügen" : "Insert text only"}</option><option value="send">{de ? "Nachricht direkt senden" : "Send message immediately"}</option>
              </select>, de ? "Direktes Senden erfolgt einmal nach erfolgreicher, vollständiger Transkription. Der bestehende Entwurf und Anhänge werden mitgesendet. Abbrechen oder Fehler sendet nichts." : "Send once after successful, complete transcription, including the existing draft and attachments. Cancellation or failure sends nothing.")}
            </>}
            {provider?.kind === "audio" && field("Language", <select aria-label={t("hardcoded.components.TranscriptionSettings.7251bca0")} className={inputClass} value={cfg.language} onChange={e => update({ language: e.target.value })}>{languages.map(([id, name]) => <option key={id} value={id}>{name}</option>)}</select>, "Automatic detects supported languages. Azure requires a specific language.")}
            {provider?.kind === "audio" && cfg.provider !== "whisper-local" && <>
              {field("API key", <div className="flex gap-2"><input aria-label={t("hardcoded.components.TranscriptionSettings.b4e54786")} type="password" autoComplete="off" className={inputClass} value={profile?.key || ""} placeholder={profile?.hasKey ? "Saved securely" : "Enter API key"} onChange={e => setProfile({ key: e.target.value, clearKey: false })} />{profile?.hasKey && <button type="button" onClick={() => setProfile({ key: "", clearKey: true, hasKey: false })} className="px-2 text-xs text-ink-secondary">{t("hardcoded.components.TranscriptionSettings.71cab323")}</button>}</div>, "Keys use this computer's protected storage. Custom local services may not require a key.", profile?.hasKey && !profile.clearKey && !profile.key?.trim() && <span role="status" className="ml-1 text-xs text-success">{t("hardcoded.components.TranscriptionSettings.d007a7df")}</span>)}
              {["azure", "compatible"].includes(cfg.provider) && field("Endpoint", <input aria-label={t("hardcoded.components.TranscriptionSettings.48622665")} className={inputClass} value={profile?.endpoint || ""} onChange={e => setProfile({ endpoint: e.target.value })} />)}
              {cfg.provider !== "azure" && field("Model", <input aria-label={t("hardcoded.components.TranscriptionSettings.0b0deef1")} className={inputClass} value={profile?.model || provider.model || ""} onChange={e => setProfile({ model: e.target.value })} />)}
            </>}
            {cfg.provider === "whisper-local" && <>
              {field("Model", <select aria-label={t("hardcoded.components.TranscriptionSettings.628fdafa")} className={inputClass} value={cfg.localModel} disabled={downloading} onChange={e => update({ localModel: e.target.value })}>{data.local.models.map(m => <option key={m.id} value={m.id}>{m.name}{m.installed ? " · downloaded" : ""}</option>)}</select>, "Larger multilingual models require more memory and processing time.")}
              <div className="flex items-center justify-between gap-3"><span role="status" className="text-xs text-ink-secondary">{downloading ? `${data.local.progress?.label || "Downloading"} · ${data.local.progress?.percent || 0}%` : data.local.runtimeReady && data.local.models.find(m => m.id === cfg.localModel)?.installed ? "Ready offline" : "Download needed"}</span><button type="button" disabled={downloading} className="rounded-full border border-hairline px-3 py-1.5 text-xs" onClick={() => void install()}>{t("hardcoded.components.TranscriptionSettings.900e971f")}</button></div>
              {field("CPU threads", <input aria-label={t("hardcoded.components.TranscriptionSettings.cee703f7")} className={inputClass} type="number" min={1} max={16} value={cfg.threads} onChange={e => update({ threads: Number(e.target.value) })} />, "Two threads limit peak load. Recognition runs only while processing speech.")}
              <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={cfg.accelerate} onChange={e => update({ accelerate: e.target.checked })} />{t("hardcoded.components.TranscriptionSettings.4ac712fd")}</label>
              {field("Speech engine", <button type="button" className="rounded-lg border border-hairline px-3 py-2 text-xs" onClick={async () => { try { const executable = await bridge.sttPickEngine!(); if (executable) update({ executable }); } catch (e) { setError(transcriptionError(e)); } }}>{cfg.executable || data.local.executable ? "Change whisper-cli…" : "Choose whisper-cli…"}</button>, "Supported Windows/Linux systems can download the engine. On macOS install whisper-cpp with Homebrew, or choose an installed executable.")}
            </>}
          </div>
          <div className="shrink-0 pt-3"><div className="min-h-9 text-xs text-danger" role="alert">{error}</div><div className="flex justify-end"><button type="submit" disabled={busy || downloading || saved} className="rounded-full bg-accent px-5 py-2 text-sm text-white disabled:opacity-50">{busy ? (de ? "Speichern…" : "Saving…") : saved ? (de ? "✓ Gespeichert" : "✓ Saved") : (de ? "Speichern" : "Save")}</button></div></div>
        </form>}
      </section>
    </div>, document.body)}
  </>;
}
