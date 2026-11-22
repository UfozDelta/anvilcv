import { Link } from 'react-router-dom';
import { SectionMark, SiteFooter, TopNav } from '../components/landing/SiteNav';
import '../styles/landing.css';
import '../components/landing/landing-page.css';
import '../styles/docs.css';

const OVERVIEW = [
  ['One bank, many jobs',
    'Your work goes in once. Each job re-ranks the bank, so a data role gets your Kafka pipeline and a web role gets your design-system migration. You never rewrite a line.'],
  ['The model proposes, code disposes',
    'Large language models (LLMs) find stories, write candidate bullets and rank them. Deterministic code enforces length bands, drops invented numbers and duplicates, and picks the bullets that fit one page.'],
  ['Compile, do not estimate',
    'A fit check compiles the exact LaTeX before saving and reports real page count, overfull boxes and measured line counts. Character counts only produce warnings.'],
  ['AnvilCV is the engine',
    'AnvilCV renders LaTeX, compiles PDFs, stores every row, scores coverage and applies quota. You can operate it in the browser or through an AI agent; both paths use the same engine.'],
];

const BANK_STEPS: [string, string][] = [
  ['Bring your work in',
    'Connect a read-only GitHub App and import a repo pinned to a commit, or paste an existing résumé at /welcome. A rule-based importer saves each confirmed line as an approved bullet in its own story.'],
  ['Build the repo map',
    'For GitHub projects, deterministic extraction finds symbols, routes and module links, ranks modules with PageRank and counts facts such as tests and endpoints. An LLM then summarizes the top 15 modules and writes a project overview, 3–7 subsystems and 2–3 end-to-end flows.'],
  ['Find stories',
    'One LLM call picks the strongest pieces of work, prefers platform-changing changes over isolated fixes and tags each with up to three lenses: ai-ml, backend, data or general. Quotes that do not appear in the source are dropped. A project holds at most 12 live stories.'],
  ['Write wordings',
    'Each new story becomes one slot on its best lens. The slot asks for 15 wordings, each labelled outcome, decision, scale or failure.'],
  ['Filter and judge',
    'Code removes banned openers, activity counts, filler, numbers absent from the source and off-band lengths. The top five go to a recruiter-style judge, run twice in different orders to cancel position bias; the best one or two wordings are kept.'],
  ['Review and approve',
    'Approve or reject bullets on the Bullets tab. Approved bullets are never changed by a later generation, and reruns skip work the bank already covers.'],
];

const TAILOR_STEPS: [string, string][] = [
  ['Read the job',
    'Paste job text or a URL. The scraper reads schema.org JobPosting JSON-LD, so Greenhouse, Lever, Workday and LinkedIn postings parse cleanly.'],
  ['Build a coverage matrix',
    'The JD is split into hard gates, minimum and preferred qualifications, and responsibility verbs. Each item is scored against the text that will actually render as Covered, Thin or Missing. Cleanup also infers one or two role lenses; keywords decide, lenses only break ties.'],
  ['Pre-filter and rank',
    'An alias-aware keyword pass keeps the top four bullets per project and 25 overall, one wording per story. The LLM ranks what survives using the same extracted JD items.'],
  ['Select in code',
    'The selector takes up to six entries of three bullets under a one-page line budget, with at least two experience and three project entries. It removes near-duplicates, keeps one wording per story and avoids repeating an opening verb within an entry.'],
  ['Fill skills and courses',
    'The LLM picks relevant categories and coursework. Skills come only from your profile; JD keywords lead each row. The four technical rows grow to the printed line width with more profile skills, measured by a real TeX compile so the 9pt font never shrinks.'],
  ['Render and compile',
    'The calibrated 10.5pt LaTeX template produces the page and Tectonic compiles the PDF. A fit check can compile the exact candidate before saving.'],
  ['Score and save',
    'An applicant tracking system (ATS) report counts matched and missing keywords in bullets, skills rows and project tech lines, and reports coverage gaps and hard-gate misses. A recruiter-style review scores the page in the background. The application keeps the LaTeX, PDF, cover letter, token cost and outcome history.'],
];

const BROWSER_STEPS: [string, string][] = [
  ['Paste your résumé', 'Open /welcome, paste the text and pick a field. Text without bullet glyphs still parses.'],
  ['Confirm the bullets', 'Every line is checked and editable. Uncheck anything you do not want printed.'],
  ['Pick a job', 'Choose a posting on /jobs. The link arrives pre-filled on the new application form.'],
  ['Tailor and add contact details', 'Run the tailor, then add your contact details. Downloads are always free.'],
];

const ABILITIES = [
  ['View', 'Profile, config, projects, stories, bullets, jobs, applications and quota through the anvil_* read tools.'],
  ['Edit', 'anvil_api reaches any endpoint: profile, generation bands, project context, bullet text and status, application fields, onboarding, admin settings.'],
  ['Generate', 'Write stories and bullets through AnvilCV’s own filters and recruiter judge, then save the approved wordings into the bank.'],
  ['Tailor', 'Pick bullets per job; AnvilCV renders the LaTeX, compiles the PDF, scores ATS and coverage, and applies your quota. anvil_check_fit compiles before saving.'],
];

const TROUBLESHOOTING = [
  ['MCP dependencies — FAIL', 'Run cd mcp && npm ci, then run /setup again.'],
  ['Credentials in environment — FAIL', 'Copy mcp/.env.example to mcp/.env, fill in the three values, then restart OpenCode so it picks up the new config.'],
  ['AnvilCV reachable — FAIL', 'Check ANVIL_BASE_URL: it must be the public HTTPS URL of your AnvilCV API, not the website host. For a local backend, start it with SPRING_PROFILES_ACTIVE=local mvn spring-boot:run (needs the database, application-local.yml and tectonic).'],
];

const BANDS: [string, string, string][] = [
  ['One full line', '96–104 characters', '13–14 words'],
  ['Dead zone (rejected)', '111–163 characters', '15–22 words'],
  ['Two lines', '170–229 characters', '23–31 words'],
  ['Hard ceiling', '240 characters', '—'],
];

const PLANS: [string, string, string][] = [
  ['Free', '3 tailors per month', 'Up to 2 projects and 1 work experience'],
  ['Starter', '60 tailors per month', '5 generations per month'],
  ['Pro', '150 tailors per month', '15 generations per month'],
  ['Pack', '200 tailors total', '20 generations total'],
];

const LIMITS: [string, string][] = [
  ['Rendered line', 'About 122 characters at 10.5pt'],
  ['Stories per project', '12 live stories; a full bank makes no LLM call'],
  ['Candidates per story slot', '15 wordings written, top 5 judged, 1–2 kept'],
  ['Selection', 'Up to 6 entries × 3 bullets; at least 2 experience and 3 project entries'],
  ['Judge angles', 'outcome, decision, scale, failure'],
  ['Skills rows', 'Four technical rows fill to 95% of line width at 9pt; AI & Integrations prints as supplied'],
  ['Tailor time', 'About 1–3 minutes, dominated by LLM latency'],
];

const STORED: [string, string][] = [
  ['Account and profile', 'Username, bcrypt hash, plan, contact details, education and five skill categories.'],
  ['Projects and bank', 'Project context, pinned GitHub commit, repo map, verified evidence spans, stories and bullet wordings with status and judge notes.'],
  ['Applications', 'JD, ordered bullet ids, cover letter, ATS results, LaTeX and PDF blobs, token cost and outcome history.'],
  ['Jobs and usage', 'Job postings and your saved jobs; per-call LLM usage and cost.'],
  ['Sent to your AI provider', 'Module source heads, README, manifests, explorer file reads and generation material. The full repository is held in memory for a run and never written to disk.'],
];

const API_GROUPS: [string, string][] = [
  ['Public', 'POST /api/login, /api/register, /api/logout · GET /api/me, /api/public/stats, /api/public/jobs'],
  ['Profile and config', 'GET|PUT /api/profile · GET|PUT /api/config/generation'],
  ['Projects and bullets', '/api/projects · /api/projects/{id}/bullets/generate · /api/projects/{id}/stories/submit · /api/bullets/{id}'],
  ['Applications', 'POST /api/applications/submit · GET /api/applications/{id} · POST /api/applications/{id}/rerender · GET /api/applications/{id}/pdf'],
  ['Agent bridge', '/api/agent/check-bullets, /check-coverage, /check-fit, /save-application and the anvil_* MCP tools'],
  ['Jobs and admin', '/api/public/jobs/webhook (HMAC-signed) · /api/admin/stats, /api/admin/llm'],
];

function Steps({ items }: { items: readonly (readonly [string, string])[] }) {
  return (
    <ol className="dc-steps">
      {items.map(([title, body], i) => (
        <li className="dc-step" key={title}>
          <span className="dc-step__n">{String(i + 1).padStart(2, '0')}</span>
          <div>
            <h3>{title}</h3>
            <p>{body}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}

function Facts({ rows }: { rows: readonly (readonly [string, string])[] }) {
  return (
    <dl className="dc-facts">
      {rows.map(([term, detail]) => (
        <div className="dc-fact" key={term}>
          <dt>{term}</dt>
          <dd>{detail}</dd>
        </div>
      ))}
    </dl>
  );
}

function DataTable({ head, rows }: { head: string[]; rows: readonly (readonly string[])[] }) {
  return (
    <div className="dc-table-wrap">
      <table className="dc-table">
        <thead><tr>{head.map((h) => <th key={h}>{h}</th>)}</tr></thead>
        <tbody>
          {rows.map((row) => <tr key={row[0]}>{row.map((cell) => <td key={cell}>{cell}</td>)}</tr>)}
        </tbody>
      </table>
    </div>
  );
}

export function DocsPage() {
  return (
    <div className="lp-root lx dc-root">
      <TopNav />

      <section className="shell dc-hero" id="top">
        <span className="lp-label lp-muted">DOCUMENTATION — ANVILCV</span>
        <h1 className="lp-display dc-title">How AnvilCV<br />works.</h1>
        <p className="dc-lede">
          AnvilCV keeps a bank of résumé bullets grounded in your work, then prints a one-page PDF
          tailored to a job. Use it in the browser or drive the same engine from an AI agent.
        </p>
        <nav className="dc-toc" aria-label="On this page">
          <span className="lp-label lp-muted">ON THIS PAGE</span>
          <a href="#overview">Overview</a>
          <a href="#how-it-works">How it works</a>
          <a href="#get-started">Get started in the browser</a>
          <a href="#agent-bridge">Set up the agent bridge</a>
          <a href="#reference">Reference</a>
        </nav>
      </section>

      {/* ── EXPLANATION ── */}
      <section className="shell lx-section" id="overview">
        <SectionMark title="Overview" aside="EXPLANATION" />
        <p className="dc-lede">
          The design follows four decisions. They explain most of what the product does and why it
          behaves the way it does.
        </p>
        <div className="dc-grid">
          {OVERVIEW.map(([title, body]) => (
            <div className="dc-card" key={title}>
              <h3>{title}</h3>
              <p>{body}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="shell lx-section" id="how-it-works">
        <SectionMark title="How it works" aside="EXPLANATION" />
        <h3 className="dc-h3">Build the bank</h3>
        <Steps items={BANK_STEPS} />
        <h3 className="dc-h3 dc-h3--gap">Tailor to a job</h3>
        <Steps items={TAILOR_STEPS} />
        <div className="dc-note">
          <span className="lp-label">WHY BOTH PATHS EXIST</span>
          <p>
            An AI agent can perform the thinking steps — finding stories, writing wordings and
            ranking bullets — while AnvilCV keeps the deterministic steps. The browser path calls
            AnvilCV’s own LLM pipeline. Both end in the same renderer, compile and storage.
          </p>
        </div>
      </section>

      {/* ── HOW-TO ── */}
      <section className="shell lx-section" id="get-started">
        <SectionMark title="Get started in the browser" aside="HOW-TO" />
        <Steps items={BROWSER_STEPS} />
        <p className="dc-commands"><Link to="/welcome">OPEN THE SETUP FLOW →</Link></p>
      </section>

      <section className="shell lx-section" id="agent-bridge">
        <SectionMark title="Set up the agent bridge" aside="HOW-TO" />
        <p className="dc-lede">
          The bridge gives an OpenCode agent 16 Model Context Protocol (MCP) tools, plus commands
          and skills, so it can import your work, judge bullets, tailor a one-page PDF and save the
          result into AnvilCV. AnvilCV still renders the LaTeX, compiles the PDF, stores everything
          and applies your quota.
        </p>
        <div className="dc-note">
          <span className="lp-label">FOR TECHNICAL USERS</span>
          <p>
            Needs a checkout of this repo and an AnvilCV account. Credentials stay in a gitignored
            file on your own machine — never commit <code>mcp/.env</code>.
          </p>
        </div>

        <h3 className="dc-h3 dc-h3--gap">Prerequisites</h3>
        <ul className="dc-list">
          <li><strong>Node.js 18+</strong> and npm — for the local MCP server.</li>
          <li><strong>OpenCode</strong> — the bridge is configured as an OpenCode MCP server; the repo’s <code>.opencode/</code> folder holds the agent, commands and skills.</li>
          <li><strong>An AnvilCV account</strong> — hosted or self-hosted, with your username and password.</li>
          <li><strong>A checkout of this repo</strong> — run the agent from the repo root so it finds <code>mcp/</code> and <code>.opencode/</code>.</li>
        </ul>

        <h3 className="dc-h3 dc-h3--gap">Setup</h3>
        <ol className="dc-steps">
          <li className="dc-step">
            <span className="dc-step__n">01</span>
            <div>
              <h3>Install the MCP server dependencies</h3>
              <p>From the repo root:</p>
              <pre className="dc-code">cd mcp &amp;&amp; npm ci</pre>
            </div>
          </li>
          <li className="dc-step">
            <span className="dc-step__n">02</span>
            <div>
              <h3>Add your credentials</h3>
              <p>Copy <code>mcp/.env.example</code> to <code>mcp/.env</code> (gitignored) and fill it in:</p>
              <pre className="dc-code">{'ANVIL_BASE_URL=https://anvilcv-api.<your-host>\nANVIL_USERNAME=you\nANVIL_PASSWORD=your-password'}</pre>
              <p className="dc-step__fine">
                <code>ANVIL_BASE_URL</code> must be the public HTTPS URL of your AnvilCV API, not the
                website host. A local backend defaults to <code>http://localhost:8080</code>. Shell
                environment variables override the file if both are set.
              </p>
            </div>
          </li>
          <li className="dc-step">
            <span className="dc-step__n">03</span>
            <div>
              <h3>Open OpenCode from the repo root</h3>
              <p>
                Run <code>opencode</code> in the repository. <code>.opencode/opencode.json</code>
                registers the <code>anvilcv</code> server (<code>node mcp/index.mjs</code>), and the
                job-agent, commands and skills in <code>.opencode/</code> load automatically.
              </p>
            </div>
          </li>
          <li className="dc-step">
            <span className="dc-step__n">04</span>
            <div>
              <h3>Verify the bridge</h3>
              <p>
                Run <code>/setup</code>. It checks dependencies, credentials, reachability, login and
                session, the MCP config, the agent and the skills — then shows everything you can
                control. Fix anything it flags before continuing.
              </p>
            </div>
          </li>
          <li className="dc-step">
            <span className="dc-step__n">05</span>
            <div>
              <h3>Get to work</h3>
              <p>
                Use <code>/find-jobs</code>, <code>/generate-bullets</code>, <code>/tailor</code> and
                <code> /review</code>. Start with <code>/tailor</code>: AnvilCV compiles the PDF and
                scores coverage before saving, so you see the real result in the app.
              </p>
            </div>
          </li>
        </ol>

        <h3 className="dc-h3 dc-h3--gap">What the bridge can do</h3>
        <div className="dc-grid">
          {ABILITIES.map(([title, body]) => (
            <div className="dc-card" key={title}>
              <h3>{title}</h3>
              <p>{body}</p>
            </div>
          ))}
        </div>
        <p className="dc-commands">
          <strong>Commands:</strong> /setup · /find-jobs · /generate-bullets · /tailor · /review
        </p>

        <h3 className="dc-h3 dc-h3--gap">If something fails</h3>
        <div className="dc-grid">
          {TROUBLESHOOTING.map(([title, body]) => (
            <div className="dc-card" key={title}>
              <h3>{title}</h3>
              <p>{body}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ── REFERENCE ── */}
      <section className="shell lx-section" id="reference">
        <SectionMark title="Reference" aside="FACTS AND LIMITS" />
        <h3 className="dc-h3">Stack</h3>
        <p className="dc-lede">
          Java 21 and Spring Boot 3.4, PostgreSQL with Flyway, React 18 with Vite, Gemini or
          OpenAI-compatible LLMs, and Tectonic for LaTeX to PDF.
        </p>

        <h3 className="dc-h3 dc-h3--gap">Text bands</h3>
        <p className="dc-lede">
          The calibrated template renders about 122 characters per line. Length checks count
          rendered characters. A measured result appears only when the source states one.
        </p>
        <DataTable head={['Band', 'Characters', 'Words']} rows={BANDS} />

        <h3 className="dc-h3 dc-h3--gap">Limits</h3>
        <Facts rows={LIMITS} />

        <h3 className="dc-h3 dc-h3--gap">Plans</h3>
        <DataTable head={['Plan', 'Tailors', 'Generations']} rows={PLANS} />
        <p className="dc-lede">
          Months are calendar months in UTC. A tailor is one finished application; a generation is
          one finished bullets run. Downloads never count. Over the limit returns 402.
        </p>

        <h3 className="dc-h3 dc-h3--gap">What is stored</h3>
        <Facts rows={STORED} />

        <h3 className="dc-h3 dc-h3--gap">API surface</h3>
        <Facts rows={API_GROUPS} />

        <h3 className="dc-h3 dc-h3--gap">Operational notes</h3>
        <ul className="dc-list">
          <li>Tectonic must be installed. Without it the app boots but every PDF compile fails.</li>
          <li>Job progress lives in memory. A restart drops running jobs, and polling breaks across multiple instances.</li>
          <li>The GitHub tarball is capped at 100 MB compressed; a snapshot keeps 25 MB of text.</li>
          <li>Context fields such as tech stack and ownership come from GitHub exploration; there is no editing UI for them.</li>
          <li>Authentication uses a session cookie and BCrypt passwords. Admin routes require the admin role.</li>
        </ul>
      </section>

      <section className="lp-cta-band">
        <div className="shell lp-cta-band__inner">
          <p className="lp-display lp-cta-band__heading">Your résumé.<br />Every job.<br />On your terms.</p>
          <Link to="/login" className="lp-btn lp-btn--acid lx-btn">OPEN ANVIL &nbsp;→</Link>
        </div>
      </section>

      <footer className="shell dc-colophon">
        <span className="lp-label lp-muted">ABOUT THESE DOCS</span>
        <p>
          The structure follows the <a href="https://diataxis.fr/" rel="noopener noreferrer" target="_blank">Diátaxis documentation framework</a>;
          the wording follows the <a href="https://developers.google.com/style" rel="noopener noreferrer" target="_blank">Google developer documentation style guide</a>.
          Source of truth for the reference section is <code>docs/REFERENCE.md</code> in the repository.
        </p>
      </footer>

      <SiteFooter />
    </div>
  );
}
