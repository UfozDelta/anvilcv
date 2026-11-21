import { McpServer } from '@modelcontextprotocol/server';
import { StdioServerTransport } from '@modelcontextprotocol/server/stdio';
import * as z from 'zod/v4';
import { writeFile } from 'node:fs/promises';
import { BASE } from './env.mjs';
let cookie = '';

async function login() {
  const r = await fetch(`${BASE}/api/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: process.env.ANVIL_USERNAME,
      password: process.env.ANVIL_PASSWORD,
    }),
  });
  if (!r.ok) throw new Error(`AnvilCV login failed: ${r.status}`);
  cookie = r.headers.get('set-cookie')?.split(';')[0] ?? '';
}

async function api(path, opts = {}) {
  const send = () => fetch(`${BASE}${path}`, {
    ...opts,
    headers: {
      'Content-Type': 'application/json',
      ...(cookie ? { Cookie: cookie } : {}),
      ...opts.headers,
    },
    redirect: 'manual',
  });
  let r = await send();
  if (r.status === 401) { await login(); r = await send(); }
  if (!r.ok) throw new Error(`AnvilCV ${path}: ${r.status} ${await r.text()}`);
  return r.status === 204 ? null : r.json();
}

const post = (path, body) => api(path, { method: 'POST', body: JSON.stringify(body ?? {}) });
const out = (data) => ({ content: [{ type: 'text', text: JSON.stringify(data, null, 2) }] });

const server = new McpServer({ name: 'anvilcv', version: '1.0.0' });

// ---------- Read: live AnvilCV state ----------
server.registerTool('anvil_status', { description: 'Check connection and current plan/quota' },
  async () => out({ me: await api('/api/me'), quota: await api('/api/quota') }));

server.registerTool('anvil_profile', { description: 'Read my profile (contact, education, skills)' },
  async () => out(await api('/api/profile')));

server.registerTool('anvil_projects', { description: 'List all projects and experiences with their ids' },
  async () => out(await api('/api/projects')));

server.registerTool('anvil_bullets', {
  description: 'List the bullet bank for a project, grouped by story',
  inputSchema: z.object({ projectId: z.string().uuid() }),
}, async ({ projectId }) => out({
  stories: await api(`/api/projects/${projectId}/stories`),
  bullets: await api(`/api/projects/${projectId}/bullets`),
}));

server.registerTool('anvil_jobs', {
  description: 'Search the jobs feed',
  inputSchema: z.object({ q: z.string().optional(), remote: z.boolean().optional(), limit: z.number().optional() }),
}, async ({ q, remote, limit }) => {
  const p = new URLSearchParams();
  if (q) p.set('q', q);
  if (remote) p.set('remote', 'true');
  if (limit) p.set('limit', String(limit));
  return out(await api(`/api/public/jobs?${p}`));
});

server.registerTool('anvil_applications', { description: 'List tailorings already saved in AnvilCV' },
  async () => out(await api('/api/applications')));

server.registerTool('anvil_quota', { description: 'Read the current quota meter' },
  async () => out(await api('/api/quota')));

// ---------- Check: AnvilCV's deterministic rules, run by the agent ----------
server.registerTool('anvil_check_bullets', {
  description: 'Run AnvilCV bullet rules on draft text: length bands, vanity counts, invented numbers, banned openers, padding, near-duplicates. Use before saving.',
  inputSchema: z.object({
    texts: z.array(z.string()).min(1),
    existingTexts: z.array(z.string()).optional(),
    projectId: z.string().uuid().optional(),
    sourceContext: z.string().optional(),
  }),
}, async (req) => out(await post('/api/agent/checks/bullets', req)));

server.registerTool('anvil_check_coverage', {
  description: 'Score a JD against rendered resume text: hard gates, gaps, and keywords (Covered/Thin/Missing)',
  inputSchema: z.object({
    jdText: z.string(),
    renderedText: z.string().optional(),
    keywords: z.array(z.string()).optional(),
  }),
}, async (req) => out(await post('/api/agent/checks/coverage', req)));

// ---------- Write: persist agent-authored work into AnvilCV ----------
server.registerTool('anvil_save_story', {
  description: 'Save a story (title + evidence quotes + lenses) to a project. Returns storyId for saving wordings.',
  inputSchema: z.object({
    projectId: z.string().uuid(),
    title: z.string(),
    evidence: z.array(z.string()),
    lenses: z.array(z.string()),
  }),
}, async (req) => out(await post('/api/agent/stories', req)));

server.registerTool('anvil_save_bullets', {
  description: 'Save agent-authored wordings onto a story with angle, judge note and status (PENDING/APPROVED/REJECTED).',
  inputSchema: z.object({
    storyId: z.string().uuid(),
    bullets: z.array(z.object({
      text: z.string(),
      tags: z.array(z.string()).optional(),
      lens: z.string().optional(),
      angle: z.enum(['outcome', 'decision', 'scale', 'failure']).optional(),
      judgeNote: z.string().optional(),
      status: z.enum(['PENDING', 'APPROVED', 'REJECTED']).optional(),
    })).min(1),
  }),
}, async (req) => out(await post(`/api/agent/stories/${req.storyId}/bullets`, { bullets: req.bullets })));

server.registerTool('anvil_set_bullet_status', {
  description: 'Approve, reject, or reset a bullet already in the bank',
  inputSchema: z.object({
    bulletId: z.string().uuid(),
    status: z.enum(['PENDING', 'APPROVED', 'REJECTED']),
  }),
}, async ({ bulletId, status }) => out(await api(`/api/bullets/${bulletId}/status`, {
  method: 'PATCH', body: JSON.stringify({ status }),
})));

server.registerTool('anvil_save_application', {
  description: 'Persist a finished tailoring into AnvilCV: AnvilCV renders the LaTeX, compiles the PDF, scores ATS/coverage and applies quota.',
  inputSchema: z.object({
    jdText: z.string(),
    jdUrl: z.string().optional(),
    roleEmphasis: z.string().optional(),
    company: z.string().optional(),
    role: z.string().optional(),
    keywords: z.array(z.string()),
    selectedBulletIds: z.array(z.string().uuid()).min(1),
    selectedSkills: z.record(z.array(z.string())).optional(),
    selectedCourses: z.array(z.string()).optional(),
    coverLetter: z.string().optional(),
  }),
}, async (req) => out(await post('/api/agent/applications', req)));

server.registerTool('anvil_download_pdf', {
  description: 'Save an application PDF from AnvilCV to a local path. Return the path, not the bytes.',
  inputSchema: z.object({ applicationId: z.string().uuid(), path: z.string() }),
}, async ({ applicationId, path }) => {
  if (!cookie) await login();
  const r = await fetch(`${BASE}/api/applications/${applicationId}/pdf`, { headers: { Cookie: cookie } });
  if (!r.ok) throw new Error(`PDF download failed: ${r.status} ${await r.text()}`);
  await writeFile(path, Buffer.from(await r.arrayBuffer()));
  return out({ saved: path, applicationId });
});

// ---------- 1:1 escape hatch: call ANY AnvilCV endpoint with the logged-in session ----------
server.registerTool('anvil_api', {
  description: 'Call any AnvilCV REST endpoint with the authenticated session. Use this for features without a dedicated tool (profile, config, projects, CRUD, jobs, billing, onboarding, admin, GitHub, field, resume import). Load the anvilcv-api skill for exact paths and bodies.',
  inputSchema: z.object({
    method: z.enum(['GET', 'POST', 'PUT', 'PATCH', 'DELETE']),
    path: z.string().startsWith('/api/'),
    query: z.record(z.string()).optional(),
    body: z.unknown().optional(),
  }),
}, async ({ method, path, query, body }) => {
  const url = new URL(path, BASE);
  for (const [k, v] of Object.entries(query ?? {})) url.searchParams.set(k, v);
  return out(await api(url.pathname + url.search, {
    method,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  }));
});

await server.connect(new StdioServerTransport());
