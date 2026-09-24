import type { DatabaseSync } from "node:sqlite";

export function createMessageDeletion(options: {
  store: unknown;
  getDb: () => DatabaseSync;
  dataDir: string;
  broadcast: (event: Record<string, unknown>) => void;
  quiesce: (threadId: string) => Promise<void>;
  clearRuntime: (ids: Set<string>, threadId: string, scrubber: { clean: (value: unknown) => unknown }) => void;
  configFile: string;
  purgeCaptures?: (scrubber: unknown, threadId: string) => void;
}): {
  readonly busy: boolean;
  execute: (threadId: string, selection: { all?: boolean; ids?: string[]; excludedIds?: string[] }) => Promise<{
    ok: true; deleted: number; ids: string[]; activeLeafId: string | null; elapsedMs: number;
  }>;
};
