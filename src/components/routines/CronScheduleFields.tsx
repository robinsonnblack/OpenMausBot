import { t } from "@/lib/i18n";
import { useMemo } from "react";
import { cn } from "@/lib/cn";
import { nextCronRuns, type RoutineCronSchedule } from "../../../shared/routine-schedule";
import type { CronChoice, CronDraft } from "./cron-editor";

const fieldClass = "min-w-0 rounded-lg border border-hairline/50 bg-inset px-3 py-2 text-[12.5px] text-ink outline-none focus:border-accent [color-scheme:dark]";
const MONTHS = Array.from({ length: 12 }, (_, index) => new Intl.DateTimeFormat("en", { month: "long", timeZone: "UTC" }).format(new Date(Date.UTC(2024, index, 1))));
const TIME_ZONES = ["UTC", ...(typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("timeZone") : [])];

export function CronRunDates({ runs, timeZone, paused = false }: { runs: number[]; timeZone: string; paused?: boolean }) {
  if (!runs.length) return null;
  const format = new Intl.DateTimeFormat(undefined, { timeZone, weekday: "short", year: "numeric", month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
  return <div className="space-y-1 text-[11px] leading-relaxed text-ink-secondary" aria-label={paused ? "Paused schedule preview" : "Next scheduled runs"}>
    <div className="font-medium">{paused ? "Paused — schedule preview" : "Next runs"} · {timeZone}</div>
    {runs.map((at) => <div key={at}><time dateTime={new Date(at).toISOString()}>{format.format(at)}</time></div>)}
    <div className="pt-1">{t("hardcoded.components.routines.CronScheduleFields.49c572ab")}</div>
  </div>;
}

export function CronSchedulePreview({ schedule, paused = false }: { schedule: RoutineCronSchedule; paused?: boolean }) {
  const runs = useMemo(() => {
    try { return nextCronRuns(schedule, Date.now(), 3); } catch { return []; }
  }, [schedule]);
  return <CronRunDates runs={runs} timeZone={schedule.timeZone} paused={paused} />;
}

export function CronScheduleFields({ choice, value, onChange, runs, error }: {
  choice: CronChoice; value: CronDraft; onChange: (draft: CronDraft) => void; runs: number[]; error: string;
}) {
  const update = (patch: Partial<CronDraft>) => onChange({ ...value, ...patch });
  return <div className="space-y-3">
    {choice === "cron" ? <label className="block space-y-1.5 text-[11.5px] text-ink-secondary">
      <span>{t("hardcoded.components.routines.CronScheduleFields.ed3ab6aa")}</span>
      <input value={value.expression} onChange={(event) => update({ expression: event.target.value })} placeholder="0 9 1 * *" spellCheck={false} autoComplete="off" aria-invalid={Boolean(error)} aria-describedby="routine-cron-help routine-cron-error" className={cn(fieldClass, "w-full font-mono")} />
      <span id="routine-cron-help" className="block text-[11px]">{t("hardcoded.components.routines.CronScheduleFields.5e664b5a")}</span>
    </label> : <div className="flex flex-wrap items-end gap-2">
      {choice === "yearly" && <label className="space-y-1.5 text-[11.5px] text-ink-secondary"><span className="block">{t("hardcoded.components.routines.CronScheduleFields.e2e29da8")}</span><select value={value.month} onChange={(event) => update({ month: event.target.value })} className={fieldClass}>{MONTHS.map((month, index) => <option key={month} value={String(index + 1)}>{month}</option>)}</select></label>}
      <label className="space-y-1.5 text-[11.5px] text-ink-secondary"><span className="block">{t("hardcoded.components.routines.CronScheduleFields.84ddcebf")}</span><select value={value.day} onChange={(event) => update({ day: event.target.value })} className={fieldClass}>{Array.from({ length: 31 }, (_, index) => <option key={index + 1} value={String(index + 1)}>{index + 1}</option>)}<option value="L">{t("hardcoded.components.routines.CronScheduleFields.429b4b48")}</option></select></label>
      <label className="space-y-1.5 text-[11.5px] text-ink-secondary"><span className="block">{t("hardcoded.components.routines.CronScheduleFields.b018b042")}</span><input type="time" step={60} value={value.time} onChange={(event) => update({ time: event.target.value })} className={fieldClass} /></label>
    </div>}
    {choice !== "cron" && Number(value.day) > 28 && <p className="text-[11px] text-ink-secondary">{t("hardcoded.components.routines.CronScheduleFields.7a705826")}</p>}
    <label className="block space-y-1.5 text-[11.5px] text-ink-secondary"><span>{t("hardcoded.components.routines.CronScheduleFields.71f70a83")}</span><input list="routine-time-zones" value={value.timeZone} onChange={(event) => update({ timeZone: event.target.value })} placeholder={t("hardcoded.components.routines.CronScheduleFields.6677e11a")} spellCheck={false} autoComplete="off" aria-invalid={Boolean(error)} aria-describedby="routine-cron-error" className={cn(fieldClass, "block w-full")} /></label>
    <datalist id="routine-time-zones">{TIME_ZONES.map(zone => <option key={zone} value={zone} />)}</datalist>
    {error && <p id="routine-cron-error" role="alert" className="text-[11.5px] text-danger">{error}</p>}
    {!error && <CronRunDates runs={runs} timeZone={value.timeZone.trim()} />}
  </div>;
}
