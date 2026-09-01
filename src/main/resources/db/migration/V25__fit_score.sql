-- Fit score for the application, stored rather than recomputed on read. Both inputs drift:
-- the posting can be edited or pulled, and the bullet bank keeps growing. The score is a
-- fact about the moment the application was generated, so it is frozen with it.
alter table application
    add column if not exists fit_score int,
    add column if not exists fit_verdict text,
    add column if not exists fit_dimensions jsonb not null default '{}',
    add column if not exists fit_strengths text[] not null default '{}',
    add column if not exists fit_gaps text[] not null default '{}';
