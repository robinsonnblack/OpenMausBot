import { useState } from "react";
import { t } from "@/lib/i18n";

export function DeviceAccessControl({ name, access, disabled, onChange }: {
  name: string;
  access?: "admin" | "client";
  disabled?: boolean;
  onChange: (access: "admin" | "client") => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  async function change(next: "admin" | "client") {
    setBusy(true);
    setError(null);
    setSaved(false);
    try { await onChange(next); setSaved(true); }
    catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setBusy(false); }
  }
  return <div className="mt-3 border-t border-hairline/30 pt-3" data-device-access>
    <label className="flex flex-wrap items-center justify-between gap-2 text-[13px] text-ink">
      {t("pairingAccess.title")}
      <select aria-label={t("pairingAccess.change", { name })} value={access ?? ""}
        disabled={disabled || busy || !access} onChange={event => void change(event.target.value as "admin" | "client")}
        className="rounded-lg border border-hairline bg-card px-2 py-1 text-ink disabled:opacity-50">
        {!access && <option value="">{t("pairingAccess.updateDesktop")}</option>}
        <option value="admin">{t("remote.serverPairing.scope.admin")}</option>
        <option value="client">{t("remote.serverPairing.scope.client")}</option>
      </select>
    </label>
    <p className="mt-2 text-[12px] text-ink-secondary" aria-live="polite">
      {busy ? t("pairingAccess.saving") : access ? t(access === "admin" ? "pairingAccess.adminDetail" : "pairingAccess.clientDetail") : t("pairingAccess.updateDesktop")}
    </p>
    {access && <p className="mt-1 text-[11px] text-ink-secondary">{t("pairingAccess.noRepair")}</p>}
    {saved && !busy && <p role="status" className="mt-2 text-[12px] text-ink">✓ {t("pairingAccess.saved")}</p>}
    {error && <p role="alert" className="mt-2 text-[12px] text-danger">{t("pairingAccess.failed", { error })}</p>}
  </div>;
}
