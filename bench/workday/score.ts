// Scores scraped Workday fields against truth.json:
//   node workday/score.ts [out/<run-dir>]   (default: newest run)  ->  <run-dir>/report.md

import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

type Entry = { key: string; value: string };
type Check = { section: string; field: string; expected: string; got: string; ok: boolean };

const HERE = dirname(fileURLToPath(import.meta.url));
const truth = JSON.parse(readFileSync(join(HERE, "truth.json"), "utf8"));

const OUT = join(HERE, "out");
const runDir = process.argv[2] ?? join(OUT, readdirSync(OUT).sort().at(-1)!);

const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, " ").trim();

/** workExperience-12--jobTitle -> groups["workExperience"][12]["jobTitle"]; ordered by index. */
function groupsOf(entries: Entry[], prefix: string) {
  const byIdx = new Map<number, Entry[]>();
  for (const e of entries) {
    const m = e.key.match(new RegExp(`^${prefix}-(\\d+)--(.+)$`));
    if (!m) continue;
    const idx = Number(m[1]);
    if (!byIdx.has(idx)) byIdx.set(idx, []);
    byIdx.get(idx)!.push({ key: m[2], value: e.value });
  }
  return [...byIdx.keys()].sort((a, b) => a - b).map((k) => byIdx.get(k)!);
}

const pick = (g: Entry[] | undefined, re: RegExp) => g?.find((e) => re.test(e.key))?.value ?? "";

function check(out: Check[], section: string, field: string, expected: string, got: string, ok = norm(got) === norm(expected)) {
  out.push({ section, field, expected, got, ok });
}

function checkDate(out: Check[], section: string, field: string, ym: string, month: string, year: string, current: string) {
  if (ym === "present") return check(out, section, field, "currently work here", current ? "checked" : "unchecked", !!current);
  const [y, m] = ym.split("-").map(Number);
  // Education often only asks for a year; score month only when Workday shows one.
  const ok = Number(year) === y && (!month || Number(month) === m);
  check(out, section, field, month ? `${m}/${y}` : `${y}`, [month, year].filter(Boolean).join("/"), ok);
}

function score(entries: Entry[]) {
  const out: Check[] = [];

  // Name/phone/address are not scored: Workday fills them from the signed-in account's
  // profile, not from the uploaded resume.
  const edu = groupsOf(entries, "education");
  check(out, "education", "entry count", String(truth.education.length), String(edu.length));
  truth.education.forEach((t: any, i: number) => {
    const g = edu[i];
    const s = `education #${i + 1}`;
    check(out, s, "school", t.school, pick(g, /school/i));
    const degree = pick(g, /^degree/i);
    check(out, s, "degree", `~${t.degreeKeyword}`, degree, norm(degree).includes(t.degreeKeyword));
    const field = pick(g, /fieldOfStudy/i);
    check(out, s, "field of study", `~${t.field}`, field, norm(field).includes(norm(t.field)));
    checkDate(out, s, "start", t.start, "", pick(g, /(firstYearAttended|startDate).*Year/i), "");
    checkDate(out, s, "end", t.end, "", pick(g, /(lastYearAttended|endDate).*Year/i), "");
  });

  const exp = groupsOf(entries, "workExperience");
  check(out, "experience", "entry count", String(truth.experience.length), String(exp.length));
  truth.experience.forEach((t: any, i: number) => {
    const g = exp[i];
    const s = `experience #${i + 1}`;
    check(out, s, "title", t.title, pick(g, /^jobTitle/i));
    check(out, s, "company", t.company, pick(g, /^companyName/i));
    check(out, s, "location", t.location, pick(g, /^location/i));
    const current = pick(g, /currentlyWorkHere/i);
    checkDate(out, s, "start", t.start, pick(g, /startDate.*Month/i), pick(g, /startDate.*Year/i), "");
    checkDate(out, s, "end", t.end, pick(g, /endDate.*Month/i), pick(g, /endDate.*Year/i), current);
  });

  return out;
}

const pct = (cs: Check[]) => (cs.length ? `${cs.filter((c) => c.ok).length}/${cs.length}` : "-");
const cell = (s: string) => (s || "∅").replace(/\|/g, "\\|");

const summary = ["| variant | education | experience | total |", "|---|---|---|---|"];
const details: string[] = [];

for (const f of readdirSync(runDir).filter((f) => f.endsWith(".json")).sort()) {
  const { variant, pages } = JSON.parse(readFileSync(join(runDir, f), "utf8"));
  const entries: Entry[] = Object.values(pages).flat() as Entry[];
  const cs = score(entries);
  const by = (p: string) => cs.filter((c) => c.section.startsWith(p));
  summary.push(`| ${variant} | ${pct(by("education"))} | ${pct(by("experience"))} | **${pct(cs)}** |`);

  details.push(`\n## ${variant}\n`);
  if (!entries.some((e) => /^(education|workExperience)-\d+--/.test(e.key))) {
    details.push("_No `education-N--` / `workExperience-N--` field ids found. This tenant may use other ids; check the raw json._\n");
  }
  const bad = cs.filter((c) => !c.ok);
  if (!bad.length) details.push("All fields correct.");
  else {
    details.push("| section | field | expected | got |", "|---|---|---|---|");
    for (const c of bad) details.push(`| ${c.section} | ${c.field} | ${cell(c.expected)} | ${cell(c.got)} |`);
  }
}

const report = `# Workday parse report\n\nRun: \`${runDir}\`\n\n${summary.join("\n")}\n${details.join("\n")}\n`;
writeFileSync(join(runDir, "report.md"), report);
console.log(report);
