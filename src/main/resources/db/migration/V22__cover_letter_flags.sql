-- Figures the cover letter states that trace back to neither the selected bullets nor
-- the job description. Computed at generation time by BulletTextRules.fabricatedNumbers,
-- stored so the warning survives to every later view of the application.
alter table application
    add column if not exists cover_letter_flags text[] not null default '{}';
