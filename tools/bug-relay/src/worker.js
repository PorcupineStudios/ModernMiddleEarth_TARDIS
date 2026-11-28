// Ring Bearer bug report relay (Cloudflare Worker). The game posts a report here; the worker keeps the screenshot and
// logs in KV, serves them at /a/<file>, and opens a GitHub issue that links them. Only this worker holds the token.
//
// Bindings (wrangler.toml): ATTACH (KV namespace). Vars: REPO, LABELS, DAILY_LIMIT_PER_IP. Secrets: GITHUB_TOKEN,
// ADMIN_KEY (optional: a report whose X-Admin-Key header matches it skips the daily limit and gets the ADMIN_LABELS,
// "valid", which lets the scheduled Claude run work it: .github/workflows/claude-auto.yml).

const MAX_BODY = 16 << 20;
const MAX_TITLE = 120;
const MAX_DESCRIPTION = 8000;
const MAX_LOG = 2 << 20;
const MAX_SCREENSHOT = 12 << 20; // base64 characters
const INLINE_LOG_LINES = 120;
const PNG = [0x89, 0x50, 0x4e, 0x47];

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    try {
      if (req.method === 'GET' && url.pathname.startsWith('/a/')) return await attachment(url.pathname.slice(3), env);
      if (req.method === 'POST' && url.pathname === '/report') return await report(req, env, url.origin);
      return json({ error: 'not found' }, 404);
    } catch (e) {
      if (e instanceof Refused) return json({ error: e.message }, e.status);
      console.error(e);
      return json({ error: 'relay error' }, 500);
    }
  },
};

class Refused extends Error {
  constructor(message, status = 400) {
    super(message);
    this.status = status;
  }
}

async function attachment(name, env) {
  if (!/^[0-9a-f-]{36}\.(png|log|crash\.txt)$/.test(name)) throw new Refused('not found', 404);
  const { value, metadata } = await env.ATTACH.getWithMetadata(name, 'arrayBuffer');
  if (value === null) throw new Refused('not found', 404);
  return new Response(value, {
    headers: {
      'Content-Type': metadata?.type ?? 'application/octet-stream',
      'Cache-Control': 'public, max-age=31536000, immutable',
      'X-Content-Type-Options': 'nosniff',
    },
  });
}

async function report(req, env, origin) {
  if (Number(req.headers.get('Content-Length') ?? 0) > MAX_BODY) throw new Refused('report too large', 413);
  const text = await req.text();
  if (text.length > MAX_BODY) throw new Refused('report too large', 413);
  let r;
  try {
    r = JSON.parse(text);
  } catch {
    throw new Refused('not JSON');
  }
  const title = str(r.title, MAX_TITLE).trim();
  if (!title) throw new Refused('title missing');
  const description = str(r.description, MAX_DESCRIPTION);
  const log = str(r.log, MAX_LOG);
  const crash = str(r.crash, MAX_LOG);
  const crashName = str(r.crashName, 100).replace(/[^\w.-]/g, '');
  const meta = r.meta && typeof r.meta === 'object' ? r.meta : {};
  let png = null;
  if (r.screenshot != null) {
    png = base64(str(r.screenshot, MAX_SCREENSHOT));
    if (!PNG.every((b, i) => png[i] === b)) throw new Refused('screenshot is not a PNG');
  }

  const admin = await isAdmin(req, env);
  if (!admin) await rateLimit(req, env);

  const id = crypto.randomUUID();
  const facts = Object.entries(meta)
    .filter(([k, v]) => k !== 'mods' && (typeof v === 'string' || typeof v === 'number'))
    .map(([k, v]) => [String(k).slice(0, 40), String(v).slice(0, 300)]);
  const mods = Array.isArray(meta.mods) ? meta.mods.slice(0, 2000).map((m) => String(m).slice(0, 200)) : [];
  const logFile = [...facts.map(([k, v]) => `${k}: ${v}`), '', 'mods:', ...mods, '', '--- latest.log (tail) ---', log].join('\n');
  await put(env, `${id}.log`, logFile, 'text/plain; charset=utf-8');
  if (crash) await put(env, `${id}.crash.txt`, crash, 'text/plain; charset=utf-8');
  if (png) await put(env, `${id}.png`, png, 'image/png');

  const a = (f) => `${origin}/a/${id}.${f}`;
  const body = [
    unmention(description) || '_No description._',
    '',
    png ? `![screenshot](${a('png')})` : '_No screenshot._',
    '',
    '| | |',
    '|---|---|',
    ...facts.map(([k, v]) => `| ${cell(k)} | ${cell(v)} |`),
    '',
    `**Logs:** [latest.log tail + mod list](${a('log')})` + (crash ? ` · [crash report ${crashName}](${a('crash.txt')})` : ''),
    '',
    details(`Last ${INLINE_LOG_LINES} log lines`, log.split('\n').slice(-INLINE_LOG_LINES).join('\n')),
    crash ? details(`Crash report ${crashName} (start)`, crash.split('\n').slice(0, 80).join('\n')) : '',
    details(`Mods (${mods.length})`, mods.join('\n')),
    '',
    `<sub>Sent from the in-game bug reporter. Report id ${id}.</sub>`,
  ].join('\n');

  const labels = [...list(env.LABELS), ...(admin ? list(env.ADMIN_LABELS) : []), ...versionLabels(list(env.VERSION_MODS), mods)];
  const gh = await github(env, `issues`, { title: `[In-game] ${title}`, body: body.slice(0, 65000), labels });
  if (!gh.ok) {
    console.error('GitHub', gh.status, await gh.text());
    throw new Refused('the ticket tracker refused the report; try again later', 502);
  }
  const issue = await gh.json();
  // GitHub silently drops labels on create for some fine-grained tokens; adding them afterwards works
  if (labels.length && !issue.labels?.length) {
    const l = await github(env, `issues/${issue.number}/labels`, { labels });
    if (!l.ok) console.error('GitHub labels', l.status, await l.text());
  }
  return json({ url: issue.html_url, number: issue.number });
}

const list = (s) => (s ?? '').split(',').map((x) => x.trim()).filter(Boolean);

// "tardis_rb 0.1.0" in the mod list -> label "tardis_rb 0.1.0", only for our own mods and sane versions, so a
// forged report can't flood the repo with junk labels
function versionLabels(ids, mods) {
  return mods.map((m) => m.split(' ')).filter(([id, v, ...rest]) => ids.includes(id) && /^[\w.+-]{1,30}$/.test(v ?? '') && !rest.length)
    .map(([id, v]) => `${id} ${v}`);
}

function github(env, path, payload) {
  return fetch(`https://api.github.com/repos/${env.REPO}/${path}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${env.GITHUB_TOKEN}`,
      Accept: 'application/vnd.github+json',
      'X-GitHub-Api-Version': '2022-11-28',
      'User-Agent': 'ringbearer-bug-relay',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
}

// The pack maker's override: a header matching the ADMIN_KEY secret (compared as hashes, in constant time). A wrong key is
// no error, the report just counts against the limit like any other.
async function isAdmin(req, env) {
  const given = req.headers.get('X-Admin-Key');
  if (!env.ADMIN_KEY || !given) return false;
  const hash = async (s) => new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s)));
  const [a, b] = [await hash(given), await hash(env.ADMIN_KEY)];
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

async function rateLimit(req, env) {
  const limit = Number(env.DAILY_LIMIT_PER_IP ?? 10);
  const ip = req.headers.get('CF-Connecting-IP') ?? 'unknown';
  const hash = [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(ip)))]
    .slice(0, 12).map((b) => b.toString(16).padStart(2, '0')).join('');
  const key = `rl:${hash}:${new Date().toISOString().slice(0, 10)}`;
  // shortcut: KV is eventually consistent, so a burst from one IP can slip a few past the limit; fine for spam caps
  const n = Number((await env.ATTACH.get(key)) ?? 0);
  if (n >= limit) throw new Refused(`daily limit of ${limit} reports reached; try again tomorrow`, 429);
  await env.ATTACH.put(key, String(n + 1), { expirationTtl: 2 * 86400 });
}

function put(env, key, value, type) {
  return env.ATTACH.put(key, value, { metadata: { type } });
}

function str(v, max) {
  if (v == null) return '';
  if (typeof v !== 'string') throw new Refused('bad field type');
  if (v.length > max) throw new Refused('a field is too long', 413);
  return v;
}

function base64(s) {
  try {
    return Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
  } catch {
    throw new Refused('screenshot is not base64');
  }
}

// players' text must not ping GitHub users or teams
const unmention = (s) => s.replace(/@(?=[\w-])/g, '@​');
const cell = (s) => unmention(s).replace(/[|\n\r]/g, ' ').replace(/</g, '&lt;');

function details(summary, text) {
  if (!text) return '';
  const fence = '`````';
  return `<details><summary>${summary}</summary>\n\n${fence}\n${unmention(text).replaceAll(fence, '````​`')}\n${fence}\n\n</details>\n`;
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { 'Content-Type': 'application/json' } });
}
