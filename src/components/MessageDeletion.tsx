import { useEffect, useState } from "react";
import { Trash2, X } from "lucide-react";
import { api, useStore, type Message } from "@/state/store";
import { ConfirmDialog } from "./ConfirmDialog";

interface Selection { ids: string[]; allIds: string[] }

/** The server supplies all stored versions; the visible transcript can omit old branches. */
export function MessageDeletion({ threadId, messages }: { threadId: string; messages: Message[] }) {
  const { dispatch } = useStore();
  const [open, setOpen] = useState(false);
  const [selection, setSelection] = useState<Selection | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [confirm, setConfirm] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    setOpen(false); setSelection(null); setSelected(new Set()); setError("");
  }, [threadId]);

  async function show() {
    setOpen(true); setError(""); setSelection(null); setSelected(new Set());
    try {
      const result = await api(`/api/threads/${encodeURIComponent(threadId)}/message-selection`) as Selection;
      setSelection(result);
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
  }

  async function remove() {
    setConfirm(false); setWorking(true); setError("");
    try {
      const result = await api(`/api/threads/${encodeURIComponent(threadId)}/messages/delete`, {
        method: "POST", body: JSON.stringify({ ids: [...selected] }),
      }) as { ids: string[]; activeLeafId: string | null };
      dispatch({ type: "messagesDeleted", threadId, ids: result.ids, activeLeafId: result.activeLeafId });
      const gone = new Set(result.ids);
      setSelection((current) => current && { ids: current.ids.filter((id) => !gone.has(id)), allIds: current.allIds.filter((id) => !gone.has(id)) });
      setSelected(new Set());
    } catch (cause) { setError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setWorking(false); }
  }

  const labels = new Map(messages.map((message) => [message.id, message]));
  return <>
    <button type="button" onClick={() => void show()} title="Delete messages" aria-label="Delete messages"
      className="rounded-md p-1.5 text-ink-secondary hover:bg-raised hover:text-ink"><Trash2 size={18} /></button>
    {open && <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/60 p-5" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !working) setOpen(false); }}>
      <section role="dialog" aria-modal="true" aria-label="Delete messages" className="flex max-h-[80vh] w-full max-w-xl flex-col rounded-2xl border border-hairline bg-panel p-5 text-ink shadow-2xl">
        <div className="flex items-center justify-between"><h2 className="text-lg font-semibold">Delete messages</h2>
          <button type="button" aria-label="Close" onClick={() => setOpen(false)} disabled={working} className="rounded p-1 hover:bg-raised"><X size={18} /></button></div>
        <p className="mt-2 text-sm text-ink-secondary">Select messages to permanently remove. Older edited and retried versions are included.</p>
        <div className="mt-3 flex gap-2 text-sm">
          <button type="button" onClick={() => setSelected(new Set(selection?.allIds ?? []))} disabled={!selection || working} className="rounded px-2 py-1 hover:bg-raised">Select all</button>
          <button type="button" onClick={() => setSelected(new Set())} disabled={working || selected.size === 0} className="rounded px-2 py-1 hover:bg-raised">Clear</button>
          <span className="ml-auto py-1 text-ink-secondary">{selected.size} selected</span>
        </div>
        <div className="mt-2 min-h-20 flex-1 overflow-y-auto rounded-lg border border-hairline p-2">
          {!selection && !error && <p className="p-2 text-sm text-ink-secondary">Loading messages…</p>}
          {selection?.allIds.map((id) => { const message = labels.get(id); return <label key={id} className="flex cursor-pointer gap-3 rounded p-2 text-sm hover:bg-raised">
            <input type="checkbox" checked={selected.has(id)} disabled={working} onChange={() => setSelected((previous) => { const next = new Set(previous); if (next.has(id)) next.delete(id); else next.add(id); return next; })} />
            <span className="min-w-0 truncate">{message ? `${message.role === "user" ? "You" : "Bot"}: ${message.text || message.kind}` : `Older version · ${id}`}</span>
          </label>; })}
          {selection?.allIds.length === 0 && <p className="p-2 text-sm text-ink-secondary">No messages in this conversation.</p>}
        </div>
        {error && <p role="alert" className="mt-2 text-sm text-red-400">{error}</p>}
        <div className="mt-4 flex justify-end gap-2"><button type="button" onClick={() => setOpen(false)} disabled={working} className="rounded-lg px-3 py-2 hover:bg-raised">Done</button>
          <button type="button" onClick={() => setConfirm(true)} disabled={working || selected.size === 0} className="rounded-lg bg-red-600 px-3 py-2 font-medium text-white disabled:opacity-50">{working ? "Deleting…" : `Delete ${selected.size}`}</button></div>
      </section>
    </div>}
    <ConfirmDialog open={confirm} title={`Delete ${selected.size} messages?`} body="This permanently deletes the selected messages and cannot be undone." confirmLabel="Delete messages" tone="danger" onCancel={() => setConfirm(false)} onConfirm={() => void remove()} />
  </>;
}
