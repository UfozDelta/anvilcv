import type { Driver } from './driver';

export interface Scene { id: string; title: string; caption: string; run: (d: Driver) => Promise<void> }

const RESUME = `Maya Chen
maya@example.com | github.com/mayachen

EXPERIENCE
Backend Engineering Intern, Northwind Robotics, Austin, TX   Jun 2025 - Aug 2025
- Built a Kafka-fed telemetry ingestion service in Spring Boot, sustaining 4,000 messages per second across 12 partitions
- Reworked Postgres indexes behind the fleet dashboard, cutting p95 query latency from 1.8 seconds to 240 milliseconds
- Wrote the team on-call runbook covering 14 alerts, used to resolve two overnight incidents

PROJECTS
Anvil Pipeline, 2025
- Built a Go test sharder that splits suites by recorded runtime across containers, cutting a 22 minute CI run to 6 minutes
- Added flaky-test detection that reruns failures and quarantines repeat offenders`;

const JD = `Backend Engineer Intern, Northwind Robotics

You will build the services that ingest telemetry from our robot fleet.
Requirements: Java or Kotlin, Spring Boot, Kafka, PostgreSQL, Docker.
Nice to have: Kubernetes, observability, on-call experience.`;

const nav = (href: string) => `.an-links a[href="${href}"]`;

export const SCENES: Scene[] = [
  {
    id: 'signup', title: 'Sign up', caption: 'Create the account.',
    async run(d) {
      await d.sleep(900);
      await d.type('input[autocomplete="username"]', 'maya.chen');
      await d.type('input[type="email"]', 'maya@example.com');
      await d.type('input[autocomplete="new-password"]', 'correct-horse-9');
      await d.type(() => d.doc.querySelectorAll('input[autocomplete="new-password"]')[1], 'correct-horse-9');
      await d.click('form button[type="submit"]');
    },
  },
  {
    id: 'welcome', title: 'Paste résumé', caption: 'The guide says what to do; the cursor does it.',
    async run(d) {
      await d.readTip(/Import your résumé/);
      await d.click(d.byText('.na-lenses button', /^Software$/));
      await d.type('.fr-paste', RESUME);
      await d.click('.fr-parse');
    },
  },
  {
    id: 'confirm', title: 'Confirm bullets', caption: 'Their own lines, taken as true. One click.',
    async run(d) {
      await d.find('.fr-entry');
      await d.sleep(1400);
      await d.click('.fr-lines li:last-child input[type="checkbox"]');
      await d.sleep(700);
      await d.click('.fr-lines li:last-child input[type="checkbox"]');
      await d.readTip(/Check your bullets/);
      await d.click('.fr-go');
    },
  },
  {
    id: 'tailor', title: 'Pick a job', caption: 'Choose a posting from Jobs; the page is built for it.',
    async run(d) {
      await d.readTip(/Pick a job/);
      await d.click('.ap-btn--acid.jb-btn');
      // The posting's link arrives pre-filled on New application, so only Run is left.
      await d.readTip(/Run it/, 1200);
      await d.click(d.byText('button[type="submit"]', /Run pipeline/));
      await d.find(d.byText('button', /VIEW APPLICATION/), 40000);
      await d.sleep(1600);
      await d.click(d.byText('button', /VIEW APPLICATION/));
    },
  },
  {
    id: 'page', title: 'Your page', caption: 'Preview the PDF. Download is always free.',
    async run(d) {
      await d.find('iframe[title="resume PDF"]', 20000);
      await d.sleep(2200);
      const link = await d.find(d.byText('a.minibtn', /Download PDF/));
      // The real link opens the PDF in a new tab; in the walkthrough only the click matters.
      link.addEventListener('click', e => e.preventDefault(), { once: true });
      await d.click(() => link);
      await d.sleep(1200);
    },
  },
  {
    id: 'profile', title: 'Add details', caption: 'Contact details, asked once a page exists.',
    async run(d) {
      await d.takeMeThere(/Add your contact details/);
      await d.find('.pf-field input');
      await d.readTip(/Add your contact details/, 1200);
      const inputs = () => [...d.doc.querySelectorAll<HTMLElement>('.pf-field input')];
      await d.type(() => inputs()[0], 'Maya Chen');
      await d.type(() => inputs()[1], 'maya@example.com');
      await d.type(() => inputs()[2], '512 555 0142');
      await d.type(() => inputs()[3], 'mayachen');
      await d.type(() => inputs()[4], 'mayachen');
      await d.click(d.byText('button[type="submit"]', /Save profile/));
      await d.sleep(1000);
    },
  },
  {
    id: 'done', title: 'Onboarding done', caption: 'The guide steps aside; the app is theirs.',
    async run(d) {
      await d.gone('.ob-strip');
      await d.sleep(2500);
    },
  },
];
