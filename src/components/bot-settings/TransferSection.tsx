import { useEffect, useState } from "react";
import { api, useStore, type Bot } from "@/state/store";
import { t } from "@/lib/i18n";
import type { LocaleKey } from "@/locales";
import { TRANSFER_SETTINGS, settingsTransferPatch } from "../../../shared/bot-settings-transfer";
import { ConfirmDialog } from "../ConfirmDialog";

export function TransferSection({ bot }: { bot: Bot }) {
  const { state, flushBotPatches } = useStore();
  const [fields, setFields] = useState<string[]>([]), [targets, setTargets] = useState<string[]>([]);
  const [snapshot, setSnapshot] = useState<Record<string, unknown> | null>(null);
  const [busy, setBusy] = useState(false), [error, setError] = useState(""), [result, setResult] = useState("");
  useEffect(() => { setFields([]); setTargets([]); setSnapshot(null); setResult(""); setError(""); }, [bot.id]);
  const toggle = (values: string[], key: string) => values.includes(key) ? values.filter(value => value !== key) : [...values, key];
  const review = async () => {
    setBusy(true); setError(""); setResult("");
    try {
      await flushBotPatches(bot.id);
      const source = await api(`/api/bots/${bot.id}/transfer-settings`);
      setSnapshot(settingsTransferPatch(source.settings, fields));
    } catch (e) { setError(String(e instanceof Error ? e.message : e)); }
    finally { setBusy(false); }
  };
  const apply = async () => {
    if (!snapshot || busy) return;
    setBusy(true); setError("");
    const failures: string[] = [], done: string[] = [];
    for (const id of targets) {
      try {
        await flushBotPatches(id);
        await api(`/api/bots/${id}`, { method: "PATCH", body: JSON.stringify(snapshot) });
        done.push(id);
      } catch (e) { failures.push(`${state.bots.find(b => b.id === id)?.name ?? id}: ${e instanceof Error ? e.message : String(e)}`); }
    }
    setTargets(current => current.filter(id => !done.includes(id)));
    setSnapshot(null); setBusy(false);
    setResult(t("transfer.completed", { count: String(done.length) }));
    setError(failures.join("\n"));
  };
  return <div className="space-y-4">
    <p className="text-sm text-ink-secondary">{t("transfer.description")}</p>
    <fieldset disabled={busy} className="space-y-2"><legend className="font-medium">{t("transfer.targets")}</legend>
      {state.bots.filter(b => b.id !== bot.id).map(b => <label key={b.id} className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={targets.includes(b.id)} onChange={() => setTargets(toggle(targets, b.id))} />{b.name}
      </label>)}
    </fieldset>
    <fieldset disabled={busy} className="space-y-2"><legend className="font-medium">{t("transfer.fields")}</legend>
      {Object.keys(TRANSFER_SETTINGS).map(key => <label key={key} className="flex items-center gap-2 text-sm">
        <input type="checkbox" checked={fields.includes(key)} onChange={() => setFields(toggle(fields, key))} />{t(`transfer.field.${key}` as LocaleKey)}
      </label>)}
    </fieldset>
    <button disabled={busy || !targets.length || !fields.length} onClick={() => void review()} className="rounded-lg bg-accent px-3 py-2 text-white disabled:opacity-40">{t("transfer.review")}</button>
    {result && <p role="status">{result}</p>}{error && <p role="alert" className="whitespace-pre-wrap text-danger">{error}</p>}
    <ConfirmDialog open={snapshot !== null} title={t("transfer.title")} tone="neutral" pending={busy}
      body={t("transfer.confirm", { bots: targets.map(id => state.bots.find(b => b.id === id)?.name ?? id).join(", "), fields: fields.map(key => `${t(`transfer.field.${key}` as LocaleKey)}: ${JSON.stringify(snapshot?.[key])}`).join("; ") })}
      confirmLabel={t("transfer.apply")} onCancel={() => { if (!busy) setSnapshot(null); }} onConfirm={() => void apply()} />
  </div>;
}
