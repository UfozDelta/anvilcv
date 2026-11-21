// /setup helper: verifies the agent↔AnvilCV bridge end to end.
// Run with: node mcp/setup.mjs
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const BASE = process.env.ANVIL_BASE_URL ?? 'http://localhost:8080';
const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..');
const line = (ok, label, detail) =>
  console.log(`${ok ? 'OK  ' : 'FAIL'}  ${label}${detail ? ' — ' + detail : ''}`);

let bad = 0;
const check = (ok, label, detail) => { if (!ok) bad++; line(ok, label, detail); };

check(existsSync(join(here, 'node_modules', '@modelcontextprotocol')), 'MCP dependencies',
  existsSync(join(here, 'node_modules', '@modelcontextprotocol')) ? undefined : 'run: cd mcp && npm install');

check(!!process.env.ANVIL_USERNAME && !!process.env.ANVIL_PASSWORD, 'Credentials in environment',
  'set ANVIL_USERNAME and ANVIL_PASSWORD before starting OpenCode');

if (!process.env.ANVIL_USERNAME || !process.env.ANVIL_PASSWORD) {
  console.log('\nPowerShell:');
  console.log('  $env:ANVIL_USERNAME="your-user"');
  console.log('  $env:ANVIL_PASSWORD="your-password"');
  console.log('  opencode   # restart so config substitution picks them up');
  process.exit(1);
}

try {
  const ping = await fetch(`${BASE}/api/ping`, { signal: AbortSignal.timeout(5000) });
  check(ping.ok, 'AnvilCV reachable', `${BASE} → ${ping.status}`);
} catch (e) {
  check(false, 'AnvilCV reachable', `${BASE} — ${e.message}`);
  console.log('\nStart it: SPRING_PROFILES_ACTIVE=local mvn spring-boot:run');
  process.exit(1);
}

try {
  const res = await fetch(`${BASE}/api/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: process.env.ANVIL_USERNAME, password: process.env.ANVIL_PASSWORD }),
    signal: AbortSignal.timeout(10000),
  });
  check(res.ok, 'Login', `${res.status}`);
  const me = await fetch(`${BASE}/api/me`, { headers: { Cookie: res.headers.get('set-cookie')?.split(';')[0] ?? '' } });
  check(me.ok, 'Session works', `${me.status}`);
} catch (e) {
  check(false, 'Login', e.message);
}

check(existsSync(join(root, '.opencode', 'opencode.json')), 'OpenCode MCP config');
check(existsSync(join(root, '.opencode', 'agents', 'job-agent.md')), 'job-agent');
for (const s of ['anvilcv-sop', 'jd-analysis', 'story-bank', 'resume-render', 'context-extractor', 'anvilcv-api']) {
  check(existsSync(join(root, '.opencode', 'skills', s, 'SKILL.md')), `Skill: ${s}`);
}

console.log(bad === 0 ? '\nSetup complete. Use /find-jobs, /generate-bullets, /tailor, /review.'
                      : `\n${bad} check(s) failed.`);
process.exit(bad === 0 ? 0 : 1);
