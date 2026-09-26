import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, privateDecrypt, constants, createDecipheriv } from 'node:crypto';
import { prepareSttImport } from './stt-import.mjs';
const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const request = () => ({ type: 'openmausbot:stt-import', requestId: 'test-request', deviceId: 'paired-phone', expiresAt: Date.now() + 90000, publicKey: publicKey.export({format:'der',type:'spki'}).toString('base64') });
const load = async () => ({ provider: 'openrouter', language: 'de-DE', profiles: { openrouter: { key: 'synthetic-secret', model: 'test', endpoint: '' }, unrelated: { key: 'must-not-export' } } });
test('exports only STT profiles, encrypted for the requesting phone', async () => {
  const result = await prepareSttImport(request(), { load, approve: async () => true });
  assert(!JSON.stringify(result).includes('synthetic-secret'));
  const e = result.envelope;
  const aes = privateDecrypt({key:privateKey,padding:constants.RSA_PKCS1_OAEP_PADDING,oaepHash:'sha256'},Buffer.from(e.wrappedKey,'base64'));
  const cipher = createDecipheriv('aes-256-gcm', aes, Buffer.from(e.iv,'base64'));
  cipher.setAAD(Buffer.from(e.requestId));
  const bytes = Buffer.from(e.ciphertext,'base64'); cipher.setAuthTag(bytes.subarray(-16));
  const json = JSON.parse(Buffer.concat([cipher.update(bytes.subarray(0,-16)),cipher.final()]));
  assert.equal(json.profiles.openrouter.key,'synthetic-secret'); assert.deepEqual(Object.keys(json.profiles),['openrouter']);
});
test('decline does not even read desktop keys', async () => {
  const result = await prepareSttImport(request(), { load: () => { throw Error('must not read'); }, approve: async () => false });
  assert.equal(result.error,'Import declined on the desktop.'); assert(!result.envelope);
});
test('rejects expired requests, malformed keys and unsupported key sizes', async () => {
  for (const bad of [{...request(), expiresAt:0},{...request(),publicKey:'invalid'}, {...request(),deviceId:'../bad'}]) {
    let called=false; const result=await prepareSttImport(bad,{load,approve:async()=>{called=true;return true;}});
    assert(result.error); assert(!called); assert(!result.envelope);
  }
});
test('empty credentials produce an explicit failure', async () => {
  const result=await prepareSttImport(request(),{load:async()=>({profiles:{}}),approve:async()=>true});
  assert.equal(result.error,'No STT keys are saved on this desktop.');
});
