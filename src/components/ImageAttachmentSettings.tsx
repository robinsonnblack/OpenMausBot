import { useEffect, useState } from "react";
import { api, useStore, type ConfigStatus } from "@/state/store";
import { activeLocale } from "@/lib/i18n";
import { imageAttachmentLimits } from "../../shared/image-attachment-limits";

export function ImageAttachmentSettings() {
  const { state, dispatch } = useStore();
  const de = activeLocale().startsWith("de");
  const word = (german: string, english: string) => de ? german : english;
  const current = imageAttachmentLimits(state.config?.imageAttachments);
  const [count, setCount] = useState(String(current.maxImages));
  const [mb, setMb] = useState(String(current.maxTotalImageBytes / 1_000_000));
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState("");
  const [help, setHelp] = useState(false);
  const [dirty, setDirty] = useState(false);
  useEffect(() => {
    if (!dirty) { setCount(String(current.maxImages)); setMb(String(current.maxTotalImageBytes / 1_000_000)); }
  }, [current.maxImages, current.maxTotalImageBytes, dirty]);
  async function save() {
    const requested = { maxImages: Number(count), maxTotalImageBytes: Math.round(Number(mb.replace(",", ".")) * 1_000_000) };
    if (!Number.isSafeInteger(requested.maxImages) || requested.maxImages <= 0 || !Number.isSafeInteger(requested.maxTotalImageBytes) || requested.maxTotalImageBytes <= 0) {
      setError(word("Gib eine positive ganze Bildanzahl und eine positive Größe in MB ein.", "Enter a positive whole image count and a positive size in MB.")); return;
    }
    setBusy(true); setSaved(false); setError("");
    try {
      const config = await api<ConfigStatus>("/api/config", { method: "PATCH", body: JSON.stringify({ imageAttachments: requested }) });
      if (config.imageAttachments?.maxImages !== requested.maxImages || config.imageAttachments?.maxTotalImageBytes !== requested.maxTotalImageBytes) throw new Error(word("Der Server hat die Werte nicht bestätigt.", "The server did not confirm the values."));
      dispatch({ type: "configStatus", config }); setDirty(false); setSaved(true);
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  }
  const input = "min-w-0 w-full rounded-lg border border-hairline bg-inset p-2 text-ink";
  return <section className="rounded-xl border border-hairline bg-card p-4" aria-label={word("Bildanhänge", "Image attachments")}>
    <div className="mb-3 flex items-center justify-between"><h3 className="font-medium">{word("Bildanhänge", "Image attachments")}</h3><button type="button" aria-label={word("Hilfe zu Bildgrenzen", "Image limits help")} onClick={() => setHelp(true)} className="rounded-lg border border-hairline px-3 py-1">?</button></div>
    <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      <label>{word("Maximale Bildanzahl", "Maximum number of images")}<input className={input} inputMode="numeric" type="number" min="1" step="1" value={count} disabled={busy} onChange={event => { setCount(event.target.value); setDirty(true); setSaved(false); }} /></label>
      <label>{word("Gesamtgröße der Bilder (MB)", "Total image size (MB)")}<input className={input} inputMode="decimal" type="number" min="0.000001" step="any" value={mb} disabled={busy} onChange={event => { setMb(event.target.value); setDirty(true); setSaved(false); }} /></label>
    </div>
    <button type="button" disabled={busy || saved} onClick={() => void save()} className="mt-3 rounded-lg border border-hairline px-3 py-2 disabled:opacity-60">{busy ? word("Speichern…", "Saving…") : saved ? word("✓ Grenzen gespeichert", "✓ Limits saved") : word("Grenzen speichern", "Save limits")}</button>
    {error && <p role="alert" className="mt-2 text-danger">{error}</p>}
    {help && <div className="fixed inset-0 z-[110] flex items-center justify-center bg-black/40 p-4"><section role="dialog" aria-modal="true" aria-label={word("Bildgrenzen", "Image limits")} className="max-w-md rounded-xl bg-card p-5 text-ink"><p>{word("Diese Grenzen gelten pro Nachricht für Desktop und Handy. Standard: 30 Bilder und 60 MB. 1 MB sind 1.000.000 Bytes. Beide Werte können ohne feste Obergrenze erhöht werden.", "These limits apply per message to desktop and phone. Defaults: 30 images and 60 MB. 1 MB is 1,000,000 bytes. Both values can be increased without a fixed upper limit.")}</p><button type="button" autoFocus onClick={() => setHelp(false)} className="mt-3 rounded-lg border border-hairline px-3 py-2">{word("Schließen", "Close")}</button></section></div>}
  </section>;
}
