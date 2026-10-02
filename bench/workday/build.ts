// Renders truth.json through the production template once per variant:
//   node workday/build.ts [variant...]  ->  workday/build/<variant>.{tex,pdf}
// The template is read from src/ and never modified.

import { execFileSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { VARIANTS, type Variant } from "./variants.ts";

const HERE = dirname(fileURLToPath(import.meta.url));
const TEMPLATE = join(HERE, "../../src/main/resources/template/resume.tex");
const OUT = join(HERE, "build");
const TECTONIC = process.env.TECTONIC_BINARY ?? "tectonic";

const truth = JSON.parse(readFileSync(join(HERE, "truth.json"), "utf8"));

// Mirrors ApplicationRenderer.renderEducation / renderExperience / renderProjects.
function education(v: Variant) {
  return truth.education
    .map((e: any) =>
      [
        "    \\resumeSubheadingUni",
        `      {${e.school}}{${e.location}}`,
        `      {${e.degree}}{${v.dates(e.start, e.end)}}`,
        `      {${e.coursework ? `\\textbf{Coursework}: ${e.coursework}` : ""}}`,
      ].join("\n"),
    )
    .join("\n");
}

function experience(v: Variant) {
  return truth.experience
    .map((x: any) =>
      [
        "    \\resumeSubheading",
        `      {${x.title}}{${v.dates(x.start, x.end)}}`,
        `      {${x.company}}{${x.location}}`,
        "      \\resumeItemListStart",
        ...x.bullets.map((b: string) => `        \\resumeItem{${b}}`),
        "      \\resumeItemListEnd",
      ].join("\n"),
    )
    .join("\n");
}

function projects() {
  const p = truth.project;
  return [
    `      \\resumeProjectHeading{\\textbf{${p.name}} $|$ \\emph{${p.tags}}}{}`,
    "        \\resumeItemListStart",
    ...p.bullets.map((b: string) => `          \\resumeItem{${b}}`),
    "        \\resumeItemListEnd",
  ].join("\n");
}

function render(v: Variant) {
  const fill: Record<string, string> = {
    NAME: `${truth.name.first} ${truth.name.last}`,
    PHONE: truth.phone,
    EMAIL: truth.email,
    LINKEDIN_HANDLE: truth.linkedin,
    GITHUB_HANDLE: truth.github,
    PORTFOLIO_LINK: "",
    EDUCATION_ITEMS: education(v),
    EXPERIENCE_ITEMS: experience(v),
    PROJECT_ITEMS: projects(),
    SKILLS_LANGUAGES: truth.skills.languages,
    SKILLS_FRAMEWORKS: truth.skills.frameworks,
    SKILLS_DATABASES: truth.skills.databases,
    SKILLS_DEVOPS: truth.skills.devops,
    SKILLS_INTERESTS: truth.skills.interests,
  };
  // Replacer functions, so backslashes in the values are not read as $-patterns.
  let tex = readFileSync(TEMPLATE, "utf8").replace(/\{\{([A-Z_]+)\}\}/g, (m, k) => fill[k] ?? m);
  if (v.preamble) tex = tex.replace("\\begin{document}", () => `${v.preamble}\n\\begin{document}`);
  if (v.sectionNames) {
    tex = tex
      .replace("\\section{Education}", () => `\\section{${v.sectionNames!.education}}`)
      .replace("\\section{Experience}", () => `\\section{${v.sectionNames!.experience}}`);
  }
  return tex;
}

const wanted = process.argv.slice(2);
const variants = wanted.length ? VARIANTS.filter((v) => wanted.includes(v.name)) : VARIANTS;
if (!variants.length) throw new Error(`no variant matches ${wanted.join(", ")}`);

mkdirSync(OUT, { recursive: true });
for (const v of variants) {
  const texPath = join(OUT, `${v.name}.tex`);
  writeFileSync(texPath, render(v));
  execFileSync(TECTONIC, ["--chatter", "minimal", "--outdir", OUT, texPath], { stdio: "inherit" });
  console.log(`built ${v.name}.pdf  (${v.desc})`);
}
