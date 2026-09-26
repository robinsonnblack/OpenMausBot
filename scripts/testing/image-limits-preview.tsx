import { createRoot } from "react-dom/client";
import { StoreProvider } from "../../src/state/store";
import { ImageAttachmentSettings } from "../../src/components/ImageAttachmentSettings";
import { setLocale } from "../../src/lib/i18n";
import { applySkin } from "../../src/lib/skins";
setLocale("de"); applySkin("chatgpt");
createRoot(document.getElementById("root")!).render(<StoreProvider><main className="mx-auto max-w-2xl bg-app p-4 text-ink"><ImageAttachmentSettings /></main></StoreProvider>);
