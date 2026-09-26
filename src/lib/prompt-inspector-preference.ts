import { useSyncExternalStore } from "react";

export const PROMPT_INSPECTOR_KEY = "omb-show-prompt-inspector";
let choice: boolean | undefined;
const listeners = new Set<() => void>();
function read(): boolean {
  if (choice !== undefined) return choice;
  try { return globalThis.localStorage?.getItem(PROMPT_INSPECTOR_KEY) === "1"; }
  catch { return false; }
}
function notify() { for (const listener of listeners) listener(); }
function storageChanged(event: StorageEvent) {
  if (event.key !== PROMPT_INSPECTOR_KEY && event.key !== null) return;
  choice = undefined;
  notify();
}
function subscribe(listener: () => void) {
  listeners.add(listener);
  if (listeners.size === 1) window.addEventListener("storage", storageChanged);
  return () => {
    listeners.delete(listener);
    if (!listeners.size) window.removeEventListener("storage", storageChanged);
  };
}
export function setShowPromptInspector(enabled: boolean) {
  choice = enabled;
  try { globalThis.localStorage?.setItem(PROMPT_INSPECTOR_KEY, enabled ? "1" : "0"); }
  catch { /* Keep the visible choice for this session if storage is unavailable. */ }
  notify();
}
export function useShowPromptInspector() {
  return useSyncExternalStore(subscribe, read, () => false);
}
