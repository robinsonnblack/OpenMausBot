import { createServer } from "node:http";
import { afterEach, expect, it } from "vitest";
import { SttImportBridge, createSttImportRoutes } from "./stt-import.ts";
import { dispatchRoutes } from "./table.ts";
import { json, readBody } from "../harness/http.ts";
import { presetPermissions, permissionFor, permissionDenial } from "../../companion/src/permissions.ts";
const servers: ReturnType<typeof createServer>[] = [];
afterEach(async () => { await Promise.all(servers.splice(0).map(s => new Promise<void>(resolve => {s.closeAllConnections();s.close(()=>resolve());}))); });
it("requires provider access rather than ordinary voice access", () => {
 expect(permissionFor("POST", "/api/transcription/import")).toBe("providers");
 expect(permissionDenial("POST", "/api/transcription/import", "client")).toContain("providers");
 expect(permissionDenial("POST", "/api/transcription/import", "admin")).toBeNull();
});
it("authenticates the phone and forwards only the encrypted result over real HTTP", async () => {
 let bridge!: SttImportBridge; const sent: unknown[] = [];
 bridge = new SttImportBridge(message => { sent.push(message); queueMicrotask(()=>bridge.receive({type:"openmausbot:stt-import-result",requestId:message.requestId,envelope:{requestId:message.requestId,ciphertext:"encrypted-only"}}));return true; });
 const handler=createSttImportRoutes(bridge);
 const server=createServer(async(req,res)=>{
   const url=new URL(req.url!,"http://localhost");
   await dispatchRoutes([handler],{req,res,url,path:url.pathname,method:req.method!,auth:{kind:"loopback",scopes:["admin"],permissions:presetPermissions("admin")},json,readBody});
 }); servers.push(server); await new Promise<void>(done=>server.listen(0,"127.0.0.1",done));
 const address=server.address() as {port:number}; const url=`http://127.0.0.1:${address.port}/api/transcription/import`;
 const body=JSON.stringify({publicKey:"YWJj"});
 expect((await fetch(url,{method:"POST",headers:{"content-type":"application/json"},body})).status).toBe(403);
 const response=await fetch(url,{method:"POST",headers:{"content-type":"application/json","x-openmausbot-companion":"1","x-openmausbot-companion-device":"phone"},body});
 expect(response.status).toBe(200);expect(response.headers.get("cache-control")).toBe("no-store");expect(await response.json()).toMatchObject({ciphertext:"encrypted-only"});expect(sent).toHaveLength(1);
});
it("reports an absent desktop and declines without hanging", async()=> {
 const absent=new SttImportBridge(()=>false);await expect(absent.request("phone","YWJj")).rejects.toThrow("Update the desktop");
 let bridge!:SttImportBridge;bridge=new SttImportBridge(m=>{queueMicrotask(()=>bridge.receive({type:"openmausbot:stt-import-result",requestId:m.requestId,error:"Import declined on the desktop."}));return true;});
 await expect(bridge.request("phone","YWJj")).rejects.toThrow("declined");
});
