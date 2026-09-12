// Parser for the "Project Context Extractor" (content_extract.md) output.
//
// That prompt emits plain headed markdown sections, each tagged with the
// AnvilCV field it feeds (e.g. "→ AnvilCV field: **techStack**"). We map the
// section heading text to the project field and return only the sections we
// recognize, so a user can paste the whole document and auto-fill the form.

export type ExtractField =
  | 'techStack'
  | 'yourRole'
  | 'ownership'
  | 'scaleImpact'
  | 'hardestProblem'
  | 'technicalDecisions'
  | 'userImpact'
  | 'securityPosture'
  | 'description';

// Known slugs from the Category lens list in content_extract.md — kept in sync
// by hand, same list CATEGORIES in lib/api.ts is built from.
const KNOWN_CATEGORY_SLUGS = new Set([
  'ai-ml', 'backend', 'frontend', 'data', 'security', 'devops', 'systems', 'comms',
]);

// "name" and "category" are handled separately below — they don't map to an
// ExtractField (name has no drawer field; category drives lens selection).
const JSON_KEY_TO_FIELD: Record<string, ExtractField> = {
  techStack: 'techStack',
  description: 'description',
  yourRole: 'yourRole',
  ownership: 'ownership',
  scaleImpact: 'scaleImpact',
  hardestProblem: 'hardestProblem',
  technicalDecisions: 'technicalDecisions',
  userImpact: 'userImpact',
  securityPosture: 'securityPosture',
};

export interface ExtractJsonResult {
  fields: Partial<Record<ExtractField, string>>;
  name?: string;
  category: string[];
}

/**
 * Parses the newer JSON handoff format from content_extract.md (a single
 * ```json fenced object with 11 keys, printed to chat instead of written to
 * disk). Returns a specific error string on malformed or partial input rather
 * than silently returning an empty result.
 */
export function parseExtractJson(raw: string): ExtractJsonResult | { error: string } {
  if (!raw?.trim()) return { error: 'Nothing pasted.' };

  const stripped = stripOuterFence(raw).trim();
  let obj: unknown;
  try {
    obj = JSON.parse(stripped);
  } catch {
    return { error: 'Could not parse as JSON. Paste the fenced ```json block from the extractor output.' };
  }

  if (typeof obj !== 'object' || obj === null || Array.isArray(obj)) {
    return { error: 'Expected a JSON object with the extractor fields.' };
  }
  const rec = obj as Record<string, unknown>;

  const requiredKeys = ['name', 'techStack', 'description', 'yourRole', 'ownership',
    'scaleImpact', 'hardestProblem', 'technicalDecisions', 'userImpact', 'securityPosture', 'category'];
  const missing = requiredKeys.filter(k => !(k in rec));
  if (missing.length > 0) {
    return { error: `Missing key(s): ${missing.join(', ')}.` };
  }

  const fields: Partial<Record<ExtractField, string>> = {};
  for (const key of Object.keys(JSON_KEY_TO_FIELD)) {
    const v = rec[key];
    if (typeof v !== 'string') return { error: `Field "${key}" must be a string.` };
    fields[JSON_KEY_TO_FIELD[key]] = v;
  }

  const name = typeof rec.name === 'string' ? rec.name : undefined;

  const categoryRaw = rec.category;
  if (!Array.isArray(categoryRaw) || categoryRaw.length === 0) {
    return { error: '"category" must be a non-empty array of lens slugs.' };
  }
  const category = categoryRaw.filter((c): c is string => typeof c === 'string');
  const unknown = category.filter(c => !KNOWN_CATEGORY_SLUGS.has(c));
  if (unknown.length > 0) {
    return { error: `Unknown category slug(s): ${unknown.join(', ')}.` };
  }

  return { fields, name, category };
}

// Heading text (lower-cased, trimmed) → field. These mirror the "##" headings
// in content_extract.md verbatim.
const HEADING_TO_FIELD: Record<string, ExtractField> = {
  'tech stack': 'techStack',
  'your role': 'yourRole',
  'what you owned end-to-end': 'ownership',
  'scale & impact': 'scaleImpact',
  'hardest problem solved': 'hardestProblem',
  'notable technical decisions': 'technicalDecisions',
  'users & business context': 'userImpact',
  'security & compliance posture': 'securityPosture',
  'architecture overview': 'description',
};

/** Strip a single outer ``` fence if the whole paste was wrapped in one. */
function stripOuterFence(text: string): string {
  const t = text.trim();
  if (!t.startsWith('```')) return text;
  const firstNl = t.indexOf('\n');
  if (firstNl === -1) return text;
  const lastFence = t.lastIndexOf('```');
  if (lastFence <= firstNl) return text;
  return t.slice(firstNl + 1, lastFence);
}

/** Remove pointer lines and the "(paste this whole section)" noise from a body. */
function cleanBody(lines: string[]): string {
  return lines
    // Drop the "→ AnvilCV field: ..." / "→ fold into: ..." pointer lines.
    .filter(l => !/^\s*→/.test(l))
    .join('\n')
    .trim();
}

const FIELDS: ExtractField[] = [
  'techStack',
  'yourRole',
  'ownership',
  'scaleImpact',
  'hardestProblem',
  'technicalDecisions',
  'userImpact',
  'securityPosture',
  'description',
];

/**
 * Sections the extractor marks "→ fold into: **someField**" carry supporting
 * material (Standout Signal, Failure Modes Avoided, ...) rather than owning a
 * field of their own. Their headings aren't in HEADING_TO_FIELD, so without
 * this they'd be dropped on import. Read the pointer line and append the body
 * to the field it names.
 */
function foldTarget(line: string): ExtractField | null {
  const m = line.match(/^\s*→\s*fold into:\s*\*\*(\w+)\*\*/i);
  if (!m) return null;
  const name = m[1].toLowerCase();
  return FIELDS.find(f => f.toLowerCase() === name) ?? null;
}

/**
 * Parse extractor output into a partial map of fields. Only sections whose
 * heading is recognized (and whose body is non-empty) are included.
 */
export function parseExtract(raw: string): Partial<Record<ExtractField, string>> {
  const out: Partial<Record<ExtractField, string>> = {};
  if (!raw?.trim()) return out;

  const text = stripOuterFence(raw);
  const lines = text.split(/\r?\n/);

  let currentField: ExtractField | null = null;
  // True when the active section was routed here by a "fold into" pointer, so
  // its body supplements whatever the owning section already wrote.
  let appending = false;
  // Set after an unrecognized heading, while we wait to see whether its first
  // pointer line folds it into a field.
  let awaitingFold = false;
  let buf: string[] = [];

  const flush = () => {
    if (currentField) {
      const body = cleanBody(buf);
      if (body) {
        const prev = out[currentField];
        out[currentField] = appending && prev ? `${prev}\n\n${body}` : body;
      }
    }
    buf = [];
    appending = false;
  };

  for (const line of lines) {
    const heading = line.match(/^\s*#{1,6}\s+(.*\S)\s*$/);
    if (heading) {
      // New section starts — commit the previous one.
      flush();
      const key = heading[1].toLowerCase().trim();
      currentField = HEADING_TO_FIELD[key] ?? null;
      awaitingFold = currentField === null;
      continue;
    }
    // Section separators reset the active section so stray text after a
    // recognized block (e.g. the Self-Check list) isn't appended to it.
    if (/^\s*---\s*$/.test(line)) {
      flush();
      currentField = null;
      awaitingFold = false;
      continue;
    }
    if (awaitingFold) {
      if (!line.trim()) continue;
      const target = foldTarget(line);
      awaitingFold = false;
      if (target) {
        currentField = target;
        appending = true;
      }
      // Either way the pointer line itself isn't body text.
      if (target) continue;
    }
    if (currentField) buf.push(line);
  }
  flush();

  return out;
}
