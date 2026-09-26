import { createPublicKey, publicEncrypt, constants, randomBytes, createCipheriv } from 'node:crypto';
const IDS = ['openrouter', 'openai', 'groq', 'deepgram', 'azure', 'compatible'];
export async function prepareSttImport(request, { load, approve, now = Date.now }) {
  if (!request || request.type !== 'openmausbot:stt-import') return null;
  const reply = { type: 'openmausbot:stt-import-result', requestId: request.requestId };
  try {
    if (!/^[\w-]{1,128}$/.test(request.deviceId || '') || !/^[\w-]{1,128}$/.test(request.requestId || '') ||
        !Number.isFinite(request.expiresAt) || request.expiresAt <= now() || request.expiresAt > now() + 95000 ||
        typeof request.publicKey !== 'string' || request.publicKey.length > 2048) throw Error('Invalid import request.');
    const key = createPublicKey({ key: Buffer.from(request.publicKey, 'base64'), format: 'der', type: 'spki' });
    if (key.asymmetricKeyType !== 'rsa' || key.asymmetricKeyDetails?.modulusLength !== 2048) throw Error('Invalid phone encryption key.');
    if (!await approve(request.deviceId)) throw Error('Import declined on the desktop.');
    if (request.expiresAt <= now()) throw Error('Import expired. Try again.');
    const cfg = await load();
    const profiles = Object.fromEntries(IDS.filter(id => cfg.profiles?.[id]?.key || id === 'compatible' && cfg.profiles?.[id]?.endpoint)
      .map(id => [id, { key: cfg.profiles[id].key || '', model: cfg.profiles[id].model || '', endpoint: cfg.profiles[id].endpoint || '' }]));
    if (!Object.keys(profiles).length) throw Error('No STT keys are saved on this desktop.');
    const aes = randomBytes(32), iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', aes, iv);
    cipher.setAAD(Buffer.from(request.requestId));
    const payload = Buffer.from(JSON.stringify({ provider: cfg.provider, language: cfg.language, profiles }));
    const ciphertext = Buffer.concat([cipher.update(payload), cipher.final(), cipher.getAuthTag()]);
    const wrappedKey = publicEncrypt({ key, padding: constants.RSA_PKCS1_OAEP_PADDING, oaepHash: 'sha256' }, aes);
    aes.fill(0); payload.fill(0);
    return { ...reply, envelope: { requestId: request.requestId, wrappedKey: wrappedKey.toString('base64'), iv: iv.toString('base64'), ciphertext: ciphertext.toString('base64') } };
  } catch (error) {
    const allowed = ['Import declined on the desktop.', 'Import expired. Try again.', 'No STT keys are saved on this desktop.', 'Invalid import request.', 'Invalid phone encryption key.'];
    return { ...reply, error: allowed.includes(error.message) ? error.message : 'Desktop STT keys could not be read.' };
  }
}
