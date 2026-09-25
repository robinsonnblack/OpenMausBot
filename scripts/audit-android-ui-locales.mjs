import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const root = new URL('../android/app/src/main/res/', import.meta.url).pathname.replace(/^\/(?=[A-Za-z]:)/, '');
function strings(path) {
  return new Map([...readFileSync(path, 'utf8').matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)]
    .map((match) => [match[1], match[2]]));
}
const source = strings(join(root, 'values', 'strings.xml'));
for (const dir of readdirSync(root).filter((name) => /^values-[a-z]{2,3}(?:-r[A-Z]{2})?$/.test(name))) {
  const target = strings(join(root, dir, 'strings.xml'));
  const missing = [];
  const unchanged = [];
  for (const [key, value] of source) {
    if (!key.startsWith('android_')) continue;
    if (!target.has(key)) missing.push(key);
    else if (target.get(key) === value) unchanged.push(key);
  }
  console.log(`${dir}: ${missing.length} missing, ${unchanged.length} unchanged`);
  if (missing.length) console.log(`  missing: ${missing.join(', ')}`);
  if (unchanged.length) console.log(`  unchanged: ${unchanged.join(', ')}`);
}
