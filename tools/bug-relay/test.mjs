// Runs the worker against an in-memory KV and a fake GitHub: `node test.mjs` (no install needed).
import assert from 'node:assert/strict';
import worker from './src/worker.js';

const store = new Map();
const ATTACH = {
  async get(k) { return store.get(k)?.value ?? null; },
  async put(k, value, opts = {}) { store.set(k, { value, metadata: opts.metadata }); },
  async getWithMetadata(k) {
    const e = store.get(k);
    if (!e) return { value: null, metadata: null };
    const v = typeof e.value === 'string' ? new TextEncoder().encode(e.value) : e.value;
    return { value: v, metadata: e.metadata };
  },
};
const env = { ATTACH, REPO: 'owner/repo', LABELS: 'bug, from-game', VERSION_MODS: 'tardis_rb,ringbearer_api', DAILY_LIMIT_PER_IP: '2', GITHUB_TOKEN: 't', ADMIN_KEY: 'sesame', ADMIN_LABELS: 'valid' };

let sent;
const calls = [];
globalThis.fetch = async (url, init) => {
  calls.push({ url, body: JSON.parse(init.body) });
  if (url.endsWith('/labels')) return new Response('[]', { status: 200 });
  sent = { url, init, body: JSON.parse(init.body) };
  return new Response(JSON.stringify({ html_url: 'https://github.com/owner/repo/issues/7', number: 7 }), { status: 201 });
};

const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 1, 2, 3]).toString('base64');
const post = (body, ip = '1.2.3.4', key) => worker.fetch(new Request('https://relay.test/report', {
  method: 'POST', headers: { 'CF-Connecting-IP': ip, ...(key ? { 'X-Admin-Key': key } : {}) }, body: JSON.stringify(body),
}), env);
const report = { title: 'Console sparks', description: 'cc @someone', log: 'line1\n`````\nline3', screenshot: png,
  crash: 'boom', crashName: 'crash-1.txt', meta: { minecraft: '1.21.1', position: '1, 2, 3', mods: ['a 1', 'b 2', 'tardis_rb 0.2.0', 'ringbearer_api bad version!'] } };

let res = await post(report);
assert.equal(res.status, 200, await res.clone().text());
assert.deepEqual(await res.json(), { url: 'https://github.com/owner/repo/issues/7', number: 7 });
assert.equal(sent.url, 'https://api.github.com/repos/owner/repo/issues');
assert.equal(sent.init.headers.Authorization, 'Bearer t');
assert.equal(sent.body.title, '[In-game] Console sparks');
assert.deepEqual(sent.body.labels, ['bug', 'from-game', 'tardis_rb 0.2.0']);
// fake GitHub returns no labels on create, so the relay adds them in a second call
assert.deepEqual(calls[1], { url: 'https://api.github.com/repos/owner/repo/issues/7/labels', body: { labels: ['bug', 'from-game', 'tardis_rb 0.2.0'] } });
const md = sent.body.body;
assert.ok(!md.includes('@someone'), 'mention neutralised');
assert.ok(md.includes('| minecraft | 1.21.1 |'));
// log, crash and mods blocks: 6 fence lines; the log's own ````` must not add one
assert.equal(md.split('\n').filter((l) => l === '`````').length, 6, 'player text cannot close a code fence');
const id = md.match(/Report id ([0-9a-f-]{36})/)[1];
assert.ok(md.includes(`![screenshot](https://relay.test/a/${id}.png)`));

// attachments are served with their type
res = await worker.fetch(new Request(`https://relay.test/a/${id}.png`), env);
assert.equal(res.headers.get('Content-Type'), 'image/png');
assert.equal((await res.arrayBuffer()).byteLength, 7);
res = await worker.fetch(new Request(`https://relay.test/a/${id}.log`), env);
assert.match(await res.text(), /mods:\na 1\nb 2/);
assert.equal((await worker.fetch(new Request('https://relay.test/a/../secret'), env)).status, 404);

// validation and the per-IP daily limit
assert.equal((await post({ ...report, title: ' ' })).status, 400);
assert.equal((await post({ ...report, screenshot: btoa('GIF89a') })).status, 400);
assert.equal((await post(report)).status, 200);
assert.equal((await post(report)).status, 429);
assert.equal((await post(report, '5.6.7.8')).status, 200);
// the admin key skips the limit; a wrong one doesn't
assert.equal((await post(report, '1.2.3.4', 'wrong')).status, 429);
assert.equal((await post(report, '1.2.3.4', 'sesame')).status, 200);
assert.deepEqual(sent.body.labels, ['bug', 'from-game', 'valid', 'tardis_rb 0.2.0'], 'admin reports are marked valid');
assert.equal((await post(report, '1.2.3.4', 'sesame')).status, 200);
assert.equal((await post({ ...report, title: 'other' }, '9.9.9.9', 'wrong')).status, 200);
assert.ok(!sent.body.labels.includes('valid'), 'a wrong key gets no valid label');
console.log('bug-relay: all checks passed');
