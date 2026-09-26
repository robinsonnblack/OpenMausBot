import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { activeLocale, t } from "@/lib/i18n";
import { PERMISSIONS, effectivePermissions, type AccessMode, type PermissionMap } from "../../companion/src/permissions";

export function DeviceAccessControl({ name, access, permissions, cloudDesktop, disabled, onChange }: {
  name: string;
  access?: AccessMode;
  permissions?: PermissionMap;
  cloudDesktop?: boolean;
  disabled?: boolean;
  onChange: (access: AccessMode, permissions?: PermissionMap) => Promise<void>;
}) {
  const id = useId();
  const de = activeLocale().startsWith("de");
  const words = (german: string, english: string) => de ? german : english;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [editor, setEditor] = useState(false);
  const [draft, setDraft] = useState<PermissionMap>(() => effectivePermissions(access ?? "client", permissions, cloudDesktop));
  const [editorSaved, setEditorSaved] = useState(false);
  const [help, setHelp] = useState<string | null>(null);
  const editorRef = useRef<HTMLElement>(null);
  const helpRef = useRef<HTMLElement>(null);
  useEffect(() => {
    const dialog = help ? helpRef.current : editor ? editorRef.current : null;
    if (!dialog) return;
    const previous = document.activeElement as HTMLElement | null;
    const elements = () => [...dialog.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled)')];
    elements()[0]?.focus();
    const handleKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); if (help) setHelp(null); else if (!busy) setEditor(false); }
      if (event.key === "Tab") {
        const items = elements(); const first = items[0]; const last = items.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
      }
    };
    dialog.addEventListener("keydown", handleKey);
    return () => { dialog.removeEventListener("keydown", handleKey); previous?.focus(); };
  }, [editor, help, busy]);
  async function change(next: AccessMode, custom?: PermissionMap) {
    setBusy(true); setError(null); setSaved(false); setEditorSaved(false);
    try { await onChange(next, custom); setSaved(true); if (next === "custom") setEditorSaved(true); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  }
  function openEditor() {
    setDraft(effectivePermissions(access ?? "client", permissions, cloudDesktop));
    setEditorSaved(false); setError(null); setEditor(true);
  }
  const button = "rounded-lg border border-hairline bg-card px-3 py-2 text-sm text-ink hover:bg-control disabled:opacity-50";
  return <div className="mt-3 border-t border-hairline/30 pt-3" data-device-access>
    <div className="flex flex-wrap items-center justify-between gap-2 text-[13px] text-ink">
      <label htmlFor={id}>{t("pairingAccess.title")}</label>
      <button type="button" className={button} aria-label={words("Hilfe zu Verbindungsrechten", "Connection rights help")} onClick={() => setHelp(words("Vollzugriff erlaubt alle angebotenen Aktionen. Nur Chat und Freigaben erlaubt Chatfunktionen und das Bestätigen oder Ablehnen von Bot-Aktionen. Benutzerdefiniert lässt dich jedes Recht im separaten Editor festlegen.", "Full access allows every listed action. Chat and approvals allows chat features and approving or denying bot actions. Custom lets you set every permission in a separate editor."))}>?</button>
      <select id={id} aria-label={t("pairingAccess.change", { name })} value={access ?? ""}
        disabled={disabled || busy || !access} onChange={event => event.target.value === "custom" ? openEditor() : void change(event.target.value as AccessMode)}
        className="max-w-full rounded-lg border border-hairline bg-card px-2 py-1 text-ink disabled:opacity-50">
        {!access && <option value="">{t("pairingAccess.updateDesktop")}</option>}
        <option value="admin">{t("remote.serverPairing.scope.admin")}</option>
        <option value="client">{words("Nur Chat und Freigaben", "Chat and approvals only")}</option>
        <option value="custom">{words("Benutzerdefiniert", "Custom")}</option>
      </select>
    </div>
    {access === "custom" && <button type="button" disabled={busy || disabled} className={`${button} mt-2`} onClick={openEditor}>{words("Berechtigungen bearbeiten", "Edit permissions")}</button>}
    {busy && <p role="status" className="mt-2 text-sm text-ink">{t("pairingAccess.saving")}</p>}
    {saved && !busy && !editor && <p role="status" className="mt-2 text-sm text-ink">✓ {t("pairingAccess.saved")}</p>}
    {error && !editor && <p role="alert" className="mt-2 text-sm text-danger">{t("pairingAccess.failed", { error })}</p>}
    {editor && createPortal(<div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4">
      <section ref={editorRef} role="dialog" aria-modal="true" aria-label={words("Benutzerdefinierte Berechtigungen", "Custom permissions")} className="flex max-h-[90vh] w-full max-w-2xl flex-col rounded-xl border border-hairline bg-card p-5 text-ink">
        <div className="mb-3 flex items-center justify-between gap-3"><h2 className="text-lg font-semibold">{words("Benutzerdefinierte Berechtigungen", "Custom permissions")} · {name}</h2><button type="button" disabled={busy} className={button} onClick={() => setEditor(false)}>{words("Schließen", "Close")}</button></div>
        <div className="flex-1 overflow-y-auto">
          {PERMISSIONS.map(([key, labelDe, labelEn, descriptionDe, descriptionEn]) => <div key={key} className="flex items-center gap-3 border-b border-hairline/40 py-3">
            <label className="flex min-w-0 flex-1 items-center gap-3"><input type="checkbox" style={{ accentColor: "var(--color-accent)" }} checked={draft[key]} disabled={busy} onChange={event => { setDraft({ ...draft, [key]: event.target.checked }); setEditorSaved(false); setError(null); }} /><span className="break-words">{de ? labelDe : labelEn}</span></label>
            <button type="button" className={button} aria-label={`${words("Hilfe", "Help")}: ${de ? labelDe : labelEn}`} onClick={() => setHelp(de ? descriptionDe : descriptionEn)}>?</button>
          </div>)}
        </div>
        {error && <p role="alert" className="mt-3 text-sm text-danger">{t("pairingAccess.failed", { error })}</p>}
        <button type="button" disabled={busy || editorSaved} className={`${button} mt-4`} onClick={() => void change("custom", draft)}>{busy ? t("pairingAccess.saving") : editorSaved ? words("✓ Berechtigungen gespeichert", "✓ Permissions saved") : words("Speichern und verwenden", "Save and use")}</button>
      </section>
    </div>, document.body)}
    {help && createPortal(<div className="fixed inset-0 z-[110] flex items-center justify-center bg-black/40 p-4"><section ref={helpRef} role="dialog" aria-modal="true" aria-label={words("Erklärung", "Explanation")} className="w-full max-w-md rounded-xl border border-hairline bg-card p-5 text-ink"><p>{help}</p><button type="button" className={`${button} mt-4`} onClick={() => setHelp(null)}>{words("Schließen", "Close")}</button></section></div>, document.body)}
  </div>;
}
