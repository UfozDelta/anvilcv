// Placeholder data for /lab/paywall. Plans copy pages/Pricing.tsx (not exported there); keep them in sync.

export type Plan = { id: string; name: string; price: number; per: string; line: string; featured?: boolean };

export const PLANS: Plan[] = [
  { id: 'starter', name: 'STARTER', price: 10, per: '/ month', line: '60 tailors a month' },
  { id: 'pro', name: 'PRO', price: 14, per: '/ month', line: '150 tailors a month', featured: true },
  { id: 'pack', name: 'PACK', price: 39, per: 'once', line: '200 tailors, never expire' },
];
export const FREE_TAILORS = 3;

/** The manual road's steps, as the real guide labels them (components/onboarding/guide.ts). */
export const STEPS = ['Add your details', 'Add your first entry', 'Draft your bullets', 'Approve 3 bullets', 'Tailor to a job', 'Download your page'];

export const JOB = { role: 'Backend Engineering Intern', company: 'Northwind Labs' };
export const RESET = 'Nov 1';

export const PAGE = { name: 'Alex Rivera', contact: 'alex@example.com · github.com/arivera' };

export const BULLETS = [
  'Built a Kafka consumer that cut order-sync lag from 40s to 3s across 2M daily events.',
  'Rewrote the billing export in Spring Boot; nightly job went from 50 to 9 minutes.',
  'Added contract tests to 14 REST endpoints, catching 6 breaking changes before release.',
];
