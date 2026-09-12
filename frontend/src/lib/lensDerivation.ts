// Dynamic lenses replace the fixed 8-category picker: one lens per technology this project
// actually names, plus one per narrative context field this project has actually filled in.
// A different project gets a different lens list — nothing here is a guessed taxonomy.
import type { Project } from './api';

export type Lens = { slug: string; label: string; blurb: string; kind: 'tech' | 'narrative' };

// Display-only grouping: the internal 8 categories still drive generation and sort, but the
// Generate tab's overview strip shows the bank sorted into 5 market-legible names instead —
// a label, never a picker (nothing here is clickable).
export const DISPLAY_GROUPS = ['Backend', 'ML', 'DevOps', 'Infra', 'Frontend'] as const;
export const CATEGORY_TO_DISPLAY: Record<string, typeof DISPLAY_GROUPS[number]> = {
  backend: 'Backend', data: 'Backend', security: 'Backend', comms: 'Backend',
  'ai-ml': 'ML',
  devops: 'DevOps',
  systems: 'Infra',
  frontend: 'Frontend',
};

// Generic fallback content for when a bucket is picked directly, with no specific dynamic
// lens behind it — the same role the original fixed-category templates used to play.
export const BUCKET_TEMPLATES: Record<typeof DISPLAY_GROUPS[number], { text: string; category: string }> = {
  Backend: { text: 'Refactored the request pipeline, dropping p95 latency **120ms**.', category: 'backend' },
  ML: { text: 'Built a retrieval pipeline that cut irrelevant results **35%** using reranked embeddings.', category: 'ai-ml' },
  DevOps: { text: 'Cut CI runtime **9 minutes** by parallelizing the test matrix.', category: 'devops' },
  Infra: { text: 'Added backpressure to the event queue, eliminating dropped messages under load.', category: 'systems' },
  Frontend: { text: 'Rebuilt the dashboard shell, cutting first paint **400ms**.', category: 'frontend' },
};

export const TECH_CATEGORY: Record<string, string> = {
  postgres: 'backend', mysql: 'backend', openapi: 'backend', go: 'backend', http: 'backend',
  redis: 'systems', kafka: 'systems', grpc: 'systems', rust: 'systems',
  kubernetes: 'devops', docker: 'devops', terraform: 'devops',
  oauth: 'security', jwt: 'security', hipaa: 'security', encryption: 'security',
  websocket: 'comms', react: 'frontend', typescript: 'frontend', graphql: 'frontend',
  pgvector: 'ai-ml', python: 'ai-ml',
};

export const TECH_TEMPLATES: Record<string, string> = {
  postgres: 'Optimized a hot Postgres query path, cutting p95 read latency **140ms**.',
  redis: 'Introduced a Redis-backed cache in front of the pricing lookup, cutting DB load **30%**.',
  kubernetes: 'Migrated the service onto Kubernetes, enabling zero-downtime rolling deploys.',
  pgvector: 'Tuned pgvector index parameters, halving semantic search latency.',
  go: 'Rewrote the ingest worker in Go, cutting memory footprint **3x**.',
  openapi: 'Generated client SDKs straight from the OpenAPI spec, removing a manual sync step.',
  http: 'Added conditional GET support to the crawler, cutting outbound bandwidth **70%**.',
  encryption: 'Rotated token encryption to AES-256-GCM with per-tenant keys.',
};

export const NARRATIVE_LABEL: Record<string, string> = {
  yourRole: 'Your role', ownership: 'Ownership', scaleImpact: 'Scale & impact',
  hardestProblem: 'Hardest problem', technicalDecisions: 'Technical decisions',
  userImpact: 'Who it served', securityPosture: 'Security & compliance',
};

export const NARRATIVE_CATEGORY: Record<string, string> = {
  yourRole: 'backend', ownership: 'systems', scaleImpact: 'backend',
  hardestProblem: 'systems', technicalDecisions: 'systems', userImpact: 'frontend', securityPosture: 'security',
};

export function firstClause(text: string): string {
  return text.split(/[.;\n]/)[0].trim();
}

export function narrativeBullet(slug: string, project: Project): string {
  const value = (project as unknown as Record<string, string | null | undefined>)[slug] ?? '';
  const clause = firstClause(value);
  switch (slug) {
    case 'scaleImpact': return `Delivered against real scale: **${clause}**.`;
    case 'hardestProblem': return `Solved the hardest problem on the project — ${clause}.`;
    case 'technicalDecisions': return `Made a deliberate tradeoff here: ${clause}.`;
    case 'securityPosture': return `Hardened the system: ${clause}.`;
    case 'ownership': return `Owned end-to-end: ${clause}.`;
    case 'userImpact': return `Built for the people who actually used it: ${clause}.`;
    case 'yourRole': return `${clause} — shipped independently from spec to production.`;
    default: return clause;
  }
}

/** Splits a comma/slash-separated tech-stack string into individual lens candidates. */
export function techTokens(techStack: string | null | undefined): string[] {
  if (!techStack) return [];
  return techStack.split(/[,/]/).map(t => t.trim()).filter(t => t.length > 1);
}

export function deriveLenses(project: Project): Lens[] {
  const tech: Lens[] = techTokens(project.techStack).map(token => ({
    slug: token.toLowerCase(),
    label: token,
    blurb: `Bullets that specifically demonstrate ${token}`,
    kind: 'tech',
  }));

  const narrative: Lens[] = (Object.keys(NARRATIVE_LABEL) as (keyof typeof NARRATIVE_LABEL)[])
    .filter(key => Boolean((project as unknown as Record<string, string | null | undefined>)[key]))
    .map(key => ({
      slug: key,
      label: NARRATIVE_LABEL[key],
      blurb: firstClause((project as unknown as Record<string, string>)[key]),
      kind: 'narrative' as const,
    }));

  return [...tech, ...narrative];
}
