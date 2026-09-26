import { createRoot } from "react-dom/client";
import { CompanionSection } from "../../src/components/CompanionSection";
import { ServerPairingCard } from "../../src/components/ServerPairingCard";
import { applySkin } from "../../src/lib/skins";
import { setLocale } from "../../src/lib/i18n";
import { StoreProvider } from "../../src/state/store";
import "../../src/styles.css";

setLocale("de");
applySkin("chatgpt");
async function request(path: string, body?: unknown) {
  const response = await fetch(path, { method: body === undefined ? "GET" : "POST", headers: { "content-type": "application/json" }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  const result = await response.json();
  if (!response.ok) throw new Error(result.error);
  return result;
}
Object.assign(window, { ogb: { companion: {
  state: () => request("/fixture/state"),
  access: (id: string, access: string) => request("/fixture/access/" + id, { access }),
} } });
createRoot(document.getElementById("root")!).render(<StoreProvider><main className="mx-auto max-w-3xl bg-app p-6 text-ink">
  <h1 className="mb-5 text-xl font-semibold">Fernzugriff · Verbindungsrechte</h1>
  <section data-legacy><CompanionSection /></section>
  <section data-server className="mt-5"><ServerPairingCard initialSession={{ kind: "loopback" }} /></section>
</main></StoreProvider>);
