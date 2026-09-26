import { createRoot } from "react-dom/client";
import { PromptInspectorSetting } from "../../src/components/SettingsModal";
import { PromptInspectorButton } from "../../src/components/PromptInspector";
import { setLocale } from "../../src/lib/i18n";
import "../../src/styles.css";
setLocale("de");
createRoot(document.getElementById("root")!).render(<main className="bg-app p-8 text-ink">
  <PromptInspectorSetting />
  <header><PromptInspectorButton threadId="fixture-only" /></header>
</main>);
