-- Recruiter pass: grades the RENDERED page, not the candidate. fit_score (V25) answers
-- "does this person fit this job" from profile skills and project history; this answers
-- "does this page sell that person for this job", judged only on what landed on the PDF.
-- Sibling of fit_score, not a duplicate of it — the two are shown separately in the UI.
-- page_count comes from tectonic's own log, so it is a fact about the compile, not a guess.
alter table application
    add column if not exists recruiter_score int,
    add column if not exists recruiter_verdict text,
    add column if not exists recruiter_dimensions jsonb not null default '{}',
    add column if not exists recruiter_bullet_verdicts jsonb not null default '[]',
    add column if not exists recruiter_stale boolean not null default false,
    add column if not exists page_count int,
    -- The forced-negative half of the recruiter schema. The model cannot return "looks
    -- fine": it must name its weakest bullet, the JD requirement with the thinnest support,
    -- and at least two weaknesses. That constraint is the point of the call, so the answers
    -- are stored rather than left to scroll past in the live progress stream.
    add column if not exists recruiter_weaknesses text[] not null default '{}',
    add column if not exists recruiter_thinnest_requirement text,
    add column if not exists recruiter_weakest_bullet_id uuid;
