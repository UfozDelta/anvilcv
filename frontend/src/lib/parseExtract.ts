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
  | 'description';

// Heading text (lower-cased, trimmed) → field. These mirror the "##" headings
// in content_extract.md verbatim.
const HEADING_TO_FIELD: Record<string, ExtractField> = {
  'tech stack': 'techStack',
  'your role': 'yourRole',
  'what you owned end-to-end': 'ownership',
  'scale & impact': 'scaleImpact',
  'hardest problem solved': 'hardestProblem',
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
  let buf: string[] = [];

  const flush = () => {
    if (currentField) {
      const body = cleanBody(buf);
      if (body) out[currentField] = body;
    }
    buf = [];
  };

  for (const line of lines) {
    const heading = line.match(/^\s*#{1,6}\s+(.*\S)\s*$/);
    if (heading) {
      // New section starts — commit the previous one.
      flush();
      const key = heading[1].toLowerCase().trim();
      currentField = HEADING_TO_FIELD[key] ?? null;
      continue;
    }
    // Section separators reset the active section so stray text after a
    // recognized block (e.g. the Self-Check list) isn't appended to it.
    if (/^\s*---\s*$/.test(line)) {
      flush();
      currentField = null;
      continue;
    }
    if (currentField) buf.push(line);
  }
  flush();

  return out;
}
