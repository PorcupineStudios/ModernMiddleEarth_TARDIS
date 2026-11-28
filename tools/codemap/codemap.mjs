// The repo's table of contents, read live from the code so it is never stale (no install needed, Node 18+):
//   node tools/codemap/codemap.mjs                 every package, one line each (names only), and every data folder
//   node tools/codemap/codemap.mjs npc/behaviour   each class whose path contains a term: path:line, kind, name and
//                                                  the first sentence of its javadoc (several terms: any of them)
// Used by the ringbearer-map skill (.claude/skills) and the organizer step of .github/workflows/claude-auto.yml.
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const files = execFileSync('git', ['ls-files'], { encoding: 'utf8', maxBuffer: 64 << 20 }).split('\n').filter(Boolean);
const java = files.filter((f) => f.endsWith('.java'));
const terms = process.argv.slice(2);

// "lotr/src/main/java/com/porcupinestudios/lotrport/npc/Foo.java" -> ["lotr main", "npc"]
function where(f) {
  const m = f.match(/^([^/]+)\/src\/([^/]+)\/java\/(.+)\/[^/]+\.java$/);
  if (!m) return [f.split('/')[0], f.replace(/\/[^/]+$/, '')];
  const pkg = m[3].replace(/^com\/porcupinestudios\/(ringbearer\/)?[^/]+\/?/, '') || '.';
  return [`${m[1]}${m[2] === 'main' ? '' : ` (${m[2]})`}`, pkg];
}

const TYPE = /^(?:public |protected |final |abstract |sealed |non-sealed |static |strictfp )*(class|interface|record|enum|@interface) (\w+)/;

// the top-level type's kind, name, line, and the first sentence of the javadoc just above it
export function summarize(src) {
  const lines = src.split('\n');
  const at = lines.findIndex((l) => TYPE.test(l));
  if (at < 0) return null;
  const [, kind, name] = lines[at].match(TYPE);
  let i = at - 1;
  while (i >= 0 && /^\s*(@|$)/.test(lines[i])) i--;
  let doc = '';
  if (i >= 0 && lines[i].trim().endsWith('*/')) {
    let j = i;
    while (j >= 0 && !lines[j].includes('/**')) j--;
    if (j >= 0) doc = lines.slice(j, i + 1).join(' ');
  }
  doc = doc.replace(/\/\*\*|\*\//g, ' ').replace(/^\s*\*|\s\*(?=\s)/g, ' ').replace(/\{@\w+\s+([^}]*)\}/g, '$1')
    .replace(/<[^>]+>/g, ' ').replace(/&\w+;/g, ' ').replace(/\s+/g, ' ').trim();
  const end = doc.search(/\.(\s|$)/);
  if (end > 0) doc = doc.slice(0, end + 1);
  if (doc.length > 180) doc = doc.slice(0, 177) + '...';
  return { kind, name, line: at + 1, doc };
}

if (terms.length) {
  const hit = java.filter((f) => terms.some((t) => f.toLowerCase().includes(t.toLowerCase())));
  for (const f of hit) {
    const s = summarize(readFileSync(f, 'utf8'));
    console.log(s ? `${f}:${s.line}  ${s.kind} ${s.name}${s.doc ? ' — ' + s.doc : ''}` : f);
  }
  // data files that match too (tuning lives in JSON: CLAUDE.md)
  const data = files.filter((f) => /\/resources\/data\//.test(f) && terms.some((t) => f.toLowerCase().includes(t.toLowerCase())));
  for (const f of data.slice(0, 60)) console.log(f);
  if (data.length > 60) console.log(`... ${data.length - 60} more data files`);
  if (!hit.length && !data.length) console.log(`nothing matches ${terms.join(', ')}; run with no arguments for the package list`);
} else {
  const pkgs = new Map();
  for (const f of java) {
    const [mod, pkg] = where(f);
    const key = `${mod}\t${pkg}`;
    if (!pkgs.has(key)) pkgs.set(key, []);
    pkgs.get(key).push(f.replace(/^.*\//, '').replace(/\.java$/, ''));
  }
  let last = '';
  for (const [key, names] of [...pkgs].sort(([a], [b]) => a.localeCompare(b))) {
    const [mod, pkg] = key.split('\t');
    if (mod !== last) console.log(`\n## ${mod}`);
    last = mod;
    const list = names.join(' ');
    console.log(`${pkg} (${names.length}): ${list.length > 220 ? list.slice(0, 217) + '...' : list}`);
  }
  const data = new Map();
  for (const f of files) {
    const m = f.match(/^([^/]+)\/src\/[^/]+\/resources\/data\/([^/]+)\/([^/]+)\//);
    if (m) data.set(`${m[1]} data/${m[2]}/${m[3]}`, (data.get(`${m[1]} data/${m[2]}/${m[3]}`) ?? 0) + 1);
  }
  console.log('\n## data folders (tuning JSON; file counts)');
  console.log([...data].sort(([a], [b]) => a.localeCompare(b)).map(([k, n]) => `${k} (${n})`).join('\n'));
}
