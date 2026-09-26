import { createRoot } from "react-dom/client";
import { SkinPicker } from "../../src/components/SkinPicker";
import { applySkin, readSkin } from "../../src/lib/skins";
import { setLocale } from "../../src/lib/i18n";
import "../../src/styles.css";

setLocale("de");
applySkin(localStorage.getItem("omb-skin") ? readSkin() : "chatgpt");
createRoot(document.getElementById("root")!).render(
  <div className="flex h-full bg-app text-ink">
    <aside data-fixture-sidebar className="w-60 shrink-0 border-r border-hairline bg-panel p-5">
      <h1 className="mb-8 text-xl font-semibold">OpenMausBot</h1>
      <p className="mb-4 text-sm text-ink-secondary">Chats</p>
      <div className="rounded-xl bg-raised p-3">Design-Vorschau</div>
    </aside>
    <main className="min-w-0 flex-1 overflow-auto p-6">
      <h2 className="mb-6 text-xl font-semibold">Darstellung</h2>
      <SkinPicker />
    </main>
  </div>,
);
