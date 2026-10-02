// Drives a real Workday posting: Apply -> Autofill with Resume -> sign in -> upload
// -> walk to My Experience, dumping every filled field on each page. NEVER submits.
//   node --env-file-if-exists=.env workday/run.ts [variant...]
// Any step that fails falls back to "do it in the browser, press Enter", since
// selectors drift between Workday tenants.

import { chromium, type Locator, type Page } from "playwright";
import { mkdirSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { createInterface } from "node:readline/promises";

const HERE = dirname(fileURLToPath(import.meta.url));
const BUILD = join(HERE, "build");

const { WD_JOB_URL, WD_EMAIL, WD_PASSWORD } = process.env;
if (!WD_JOB_URL || !WD_EMAIL || !WD_PASSWORD) {
  throw new Error("set WD_JOB_URL, WD_EMAIL, WD_PASSWORD in bench/.env (see .env.example)");
}
const urls = WD_JOB_URL.split(",").map((u) => u.trim()).filter(Boolean);

const wanted = process.argv.slice(2);
const variants = readdirSync(BUILD)
  .filter((f) => f.endsWith(".pdf"))
  .map((f) => f.slice(0, -4))
  .filter((v) => !wanted.length || wanted.includes(v));
if (!variants.length) throw new Error("no PDFs in workday/build — run `npm run build` first");

const runDir = join(HERE, "out", new Date().toISOString().replace(/[:.]/g, "-"));
mkdirSync(runDir, { recursive: true });

const rl = createInterface({ input: process.stdin, output: process.stdout });
const pause = (msg: string) => {
  if (!process.stdin.isTTY) throw new Error(`manual step needed but no terminal attached: ${msg}`);
  return rl.question(`\n>>> ${msg}\n    press Enter when done... `);
};

async function step(name: string, fn: () => Promise<unknown>) {
  try {
    await fn();
    console.log(`  ok   ${name}`);
  } catch (e) {
    if (String(e).includes("refusing to click Submit")) throw e; // never hand Submit to the user either
    console.log(`  FAIL ${name}: ${String((e as Error).message).split("\n")[0]}`);
    await pause(`Do "${name}" by hand in the browser.`);
  }
}

const aid = (page: Page, id: string) => page.locator(`[data-automation-id="${id}"]`).first();

// Workday lays transparent "click_filter" divs over its buttons, and locator clicks often
// don't register. A real mouse click at the button's center hits whatever is on top, like a person.
async function click(page: Page, btn: Locator, timeout = 15_000) {
  await btn.waitFor({ state: "visible", timeout });
  // On the Review step the next button becomes Submit. This harness must never apply.
  if (/submit/i.test((await btn.textContent()) ?? "")) throw new Error("refusing to click Submit");
  await btn.scrollIntoViewIfNeeded();
  const box = (await btn.boundingBox())!;
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
}
const clickAid = (page: Page, id: string, timeout?: number) => click(page, aid(page, id), timeout);
const nextButton = (page: Page) =>
  page.getByRole("button", { name: /^(continue|save and continue|next)$/i }).first();

/** Answers unanswered required questions so Continue works: radios -> "No", dropdowns -> first option. */
async function answerRequired(page: Page) {
  const groups = await page.locator('input[type="radio"]').evaluateAll((els) => {
    const names = [...new Set(els.map((e) => (e as HTMLInputElement).name))];
    return names.filter((n) => !els.some((e) => (e as HTMLInputElement).name === n && (e as HTMLInputElement).checked));
  });
  for (const name of groups) {
    const no = page.locator(`input[type="radio"][name="${name}"][value="false"]`);
    const radio = (await no.count()) ? no.first() : page.locator(`input[type="radio"][name="${name}"]`).first();
    const id = await radio.getAttribute("id");
    await click(page, page.locator(`label[for="${id}"]`));
    console.log(`       answered radio ${name}`);
  }
  const dropdowns = page.locator('button[aria-haspopup="listbox"]').filter({ hasText: /^Select One$/ });
  for (let i = await dropdowns.count(); i > 0; i--) {
    const name = await dropdowns.first().getAttribute("name");
    await click(page, dropdowns.first());
    // The opened popup is appended to <body>, so it is the last listbox; earlier ones belong
    // to other fields (e.g. the selected "Canada (+1)" phone-code pill).
    const popup = page.locator('[role="listbox"]:visible').last();
    await popup.waitFor({ timeout: 5_000 });
    const option = popup.locator('[role="option"]').filter({ hasNotText: /^Select One$/ }).first();
    console.log(`       answered dropdown ${name}: ${(await option.textContent())?.trim()}`);
    await click(page, option, 5_000);
    await page.waitForTimeout(500);
  }
}

async function currentStep(page: Page) {
  return (await page.locator('[data-automation-id="progressBarActiveStep"]').first().textContent().catch(() => null)) ?? "";
}

/** Every visible form value on the page, keyed by the nearest Workday field id. */
async function dump(page: Page) {
  return page.evaluate(() => {
    const keyOf = (el: Element) => {
      const own = el.id && el.id.includes("--") ? el.id : null;
      const near = el.closest('[id*="--"]')?.id;
      return own ?? near ?? el.id ?? el.getAttribute("data-automation-id") ?? "";
    };
    const out: { key: string; aid: string | null; label: string | null; value: string }[] = [];
    const nodes = document.querySelectorAll(
      'input:not([type="file"]):not([type="hidden"]), textarea, button[aria-haspopup="listbox"], [data-automation-id="selectedItem"]',
    );
    for (const el of nodes) {
      let value: string;
      if (el instanceof HTMLInputElement && (el.type === "checkbox" || el.type === "radio")) value = el.checked ? "true" : "";
      else if (el instanceof HTMLInputElement || el instanceof HTMLTextAreaElement) value = el.value;
      else value = el.getAttribute("title") ?? el.textContent ?? "";
      value = value.trim();
      if (!value || value === "Select One") continue;
      out.push({ key: keyOf(el), aid: el.getAttribute("data-automation-id"), label: el.getAttribute("aria-label"), value });
    }
    return out;
  });
}

async function runVariant(variant: string, url: string) {
  console.log(`\n=== ${variant}  ->  ${url}`);
  const browser = await chromium.launch({ headless: false, slowMo: 100 });
  const page = await (await browser.newContext()).newPage();
  const pages: Record<string, Awaited<ReturnType<typeof dump>>> = {};

  try {
    await page.goto(url, { waitUntil: "domcontentloaded" });
    await aid(page, "legalNoticeAcceptButton").click({ timeout: 5_000 }).catch(() => {});

    // Skipped when the URL already deep-links into .../apply/autofillWithResume.
    if (!page.url().includes("autofillWithResume")) {
      await step("click Apply", () => clickAid(page, "adventureButton"));
      await step("choose Autofill with Resume", () => clickAid(page, "autofillWithResume"));
    }

    await step("sign in", async () => {
      // Some tenants open on "Create Account"; switch to sign-in first. The form ignores
      // clicks until its scripts settle, so wait for it to render and then a beat more.
      await aid(page, "email").waitFor({ state: "visible", timeout: 30_000 });
      await page.waitForTimeout(2_000);
      if (!(await aid(page, "signInSubmitButton").isVisible().catch(() => false))) {
        await clickAid(page, "signInLink", 5_000).catch(() => {});
        await aid(page, "signInSubmitButton").waitFor({ state: "visible", timeout: 10_000 });
      }
      await aid(page, "email").fill(WD_EMAIL!);
      await aid(page, "password").fill(WD_PASSWORD!);
      const submit = page.locator("form").getByRole("button", { name: "Sign In", exact: true }).first();
      await click(page, submit);
      await page.waitForTimeout(3_000);
      if (await submit.isVisible().catch(() => false)) await aid(page, "password").press("Enter");
      await page.locator('input[type="file"]').first().waitFor({ state: "attached", timeout: 30_000 });
    });

    await step("upload resume", async () => {
      // A draft from an earlier run may already hold a resume; remove it so Workday re-parses.
      const del = aid(page, "delete-file");
      while (await del.isVisible().catch(() => false)) await del.click();
      await page.locator('input[type="file"]').first().setInputFiles(join(BUILD, `${variant}.pdf`));
      await page.getByText(`${variant}.pdf`).first().waitFor({ timeout: 60_000 });
      // The filename shows client-side at once; Continue before the server parse finishes
      // drops the resume. Wait for Workday's confirmation, then give the parse time.
      await page.getByText(/successfully uploaded/i).first().waitFor({ timeout: 60_000 }).catch(() => {});
      await page.waitForTimeout(8_000);
    });

    // Walk forward until the My Experience step, dumping each page on the way.
    for (let i = 0; i < 4; i++) {
      if (/review/i.test(await currentStep(page))) break;
      await step("next page", async () => {
        const before = await currentStep(page);
        await answerRequired(page);
        await click(page, nextButton(page));
        await page.waitForFunction(
          (b) => document.querySelector('[data-automation-id="progressBarActiveStep"]')?.textContent !== b,
          before,
          { timeout: 20_000 },
        );
      });
      // The progress bar flips before the form renders; wait for the form, then let it settle.
      await nextButton(page).waitFor({ state: "visible", timeout: 30_000 }).catch(() => {});
      // A reused draft sometimes lands on "Something went wrong ... refresh"; do what it says.
      if (await page.getByText("Something went wrong").isVisible().catch(() => false)) {
        console.log("       Workday error page, reloading");
        await page.reload({ waitUntil: "domcontentloaded" });
        await nextButton(page).waitFor({ state: "visible", timeout: 30_000 }).catch(() => {});
      }
      await page.waitForLoadState("networkidle").catch(() => {});
      await page.waitForTimeout(3_000);
      const name = (await currentStep(page)) || `page${i}`;
      if (/experience/i.test(name)) {
        await page.locator('[id^="workExperience-"], [id^="education-"]').first().waitFor({ timeout: 15_000 }).catch(() => {});
      }
      pages[name] = await dump(page);
      await page.screenshot({ path: join(runDir, `${variant}--${name.replace(/\W+/g, "_")}.png`), fullPage: true });
      if (/experience/i.test(name)) break;
    }

    if (!Object.keys(pages).some((n) => /experience/i.test(n))) {
      await pause("Get to the My Experience page by hand (fill any required fields).");
      pages["My Experience"] = await dump(page);
      await page.screenshot({ path: join(runDir, `${variant}--My_Experience.png`), fullPage: true });
    }
  } finally {
    await page.screenshot({ path: join(runDir, `${variant}--last.png`), fullPage: true }).catch(() => {});
    await page.content().then((html) => writeFileSync(join(runDir, `${variant}--last.html`), html)).catch(() => {});
    writeFileSync(join(runDir, `${variant}.json`), JSON.stringify({ variant, url, pages }, null, 2));
    await browser.close();
  }
}

for (const [i, v] of variants.entries()) {
  await runVariant(v, urls[i % urls.length]).catch((e) => console.log(`  ABORT ${v}: ${String(e.message).split("\n")[0]}`));
}
rl.close();
console.log(`\nscraped -> ${runDir}\nnext: npm run score`);
