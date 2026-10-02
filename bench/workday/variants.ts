// Each variant is the production template plus one change, so a Workday score
// difference can be pinned on that change. `preamble` is inserted just before
// \begin{document} and may \renewcommand the heading macros.

export type Variant = {
  name: string;
  desc: string;
  /** Renders a start/end pair ("YYYY-MM" or "present") as the dates string. */
  dates: (start: string, end: string) => string;
  preamble?: string;
  sectionNames?: { education: string; experience: string };
};

const MON = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

const abbr = (ym: string) => {
  if (ym === "present") return "Present";
  const [y, m] = ym.split("-");
  return `${MON[Number(m) - 1]} ${y}`;
};
const numeric = (ym: string) => {
  if (ym === "present") return "Present";
  const [y, m] = ym.split("-");
  return `${m}/${y}`;
};

// What production emits today: "Jun 2023 -- Present" (LaTeX en dash).
const enDash = (s: string, e: string) => `${abbr(s)} -- ${abbr(e)}`;
const hyphen = (s: string, e: string) => `${abbr(s)} - ${abbr(e)}`;

// Every field on its own line, no right-aligned column for the PDF extractor to split.
const STACKED = String.raw`
\renewcommand{\resumeSubheading}[4]{\vspace{-2pt}\item
  \textbf{#1}\\
  \textit{\small #3, #4}\\
  \textit{\small #2}}
\renewcommand{\resumeSubheadingUni}[5]{\vspace{-2pt}\item
  \textbf{#1}, #2\\
  \textit{\small #3}\\
  \textit{\small #4}\\
  \small{#5}}
`;

// Production education macro minus the trailing \vspace{-7pt}, which overlaps the
// coursework line with the next school, so extractors merge them into one line.
const UNI_FIX = String.raw`
\renewcommand{\resumeSubheadingUni}[5]{\vspace{-2pt}\item
  \begin{tabular*}{0.97\textwidth}[t]{l@{\extracolsep{\fill}}r}
    \textbf{#1} & #2 \\[-2pt]
    \textit{\small#3} & \textit{\small #4} \\
  \end{tabular*}
  \vspace{-1pt}
  \small{#5}}
`;

// Same two-column table, but employer on line 1 and title on line 2 (education already does this).
const COMPANY_FIRST = String.raw`
\renewcommand{\resumeSubheading}[4]{\vspace{-2pt}\item
  \begin{tabular*}{0.97\textwidth}[t]{l@{\extracolsep{\fill}}r}
    \textbf{#3} & #4 \\[-2pt]
    \textit{\small#1} & \textit{\small #2} \\
  \end{tabular*}\vspace{-2pt}}
`;

// Bold instead of small caps (XeTeX small caps can extract as odd glyphs).
const PLAIN_HEADINGS = String.raw`
\titleformat{\section}{\vspace{-6pt}\bfseries\raggedright\large}{}{0em}{}[\color{black}\titlerule \vspace{-6pt}]
`;

const UPPER_NAMES = { education: "EDUCATION", experience: "WORK EXPERIENCE" };

export const VARIANTS: Variant[] = [
  { name: "baseline", desc: "production template as-is", dates: enDash },
  { name: "ascii-dates", desc: "dates use '-' not LaTeX '--' en dash", dates: hyphen },
  { name: "numeric-dates", desc: "dates as MM/YYYY - MM/YYYY", dates: (s, e) => `${numeric(s)} - ${numeric(e)}` },
  { name: "uni-fix", desc: "education: drop the -7pt vspace so coursework no longer overlaps the next school", dates: enDash, preamble: UNI_FIX },
  { name: "stacked", desc: "no right-aligned dates column; one field per line", dates: enDash, preamble: STACKED },
  { name: "company-first", desc: "experience heading: company line 1, title line 2", dates: enDash, preamble: COMPANY_FIRST },
  { name: "headings", desc: "bold 'EDUCATION' / 'WORK EXPERIENCE' headings, no small caps", dates: enDash, preamble: PLAIN_HEADINGS, sectionNames: UPPER_NAMES },
  {
    name: "ats-safe",
    desc: "stacked + ascii-dates + headings combined",
    dates: hyphen,
    preamble: STACKED + PLAIN_HEADINGS,
    sectionNames: UPPER_NAMES,
  },
];
