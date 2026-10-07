-- One row per story a bank build kept (see V37's bullet.story_id): the work its wordings
-- describe, the evidence quotes it was picked on, and the lenses it was tagged with. Lets the
-- next build see what the bank already covers. id is assigned by the app, not defaulted.
CREATE TABLE story (
    id         UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    title      TEXT NOT NULL,
    evidence   TEXT[] NOT NULL DEFAULT '{}',
    lenses     TEXT[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX story_project_id_idx ON story(project_id);

-- Backfill a story per story_id already in use. The title is the first wording, bold markers
-- stripped, cut to 120 chars; the original evidence was never stored. Lenses are the
-- categories its wordings were written for. min(text) because uuid has no min().
INSERT INTO story (id, project_id, title, evidence, lenses, created_at)
SELECT b.story_id,
       min(b.project_id::text)::uuid,
       left(btrim(regexp_replace((array_agg(b.text ORDER BY b.created_at, b.id))[1], '\*\*', '', 'g')), 120),
       '{}',
       array_agg(DISTINCT b.category),
       min(b.created_at)
FROM bullet b
WHERE b.story_id IS NOT NULL
GROUP BY b.story_id;

-- Deferred so a build can insert its bullets before their story rows, in one transaction,
-- and write a story only once at least one of its bullets was kept.
ALTER TABLE bullet
    ADD CONSTRAINT bullet_story_id_fkey FOREIGN KEY (story_id) REFERENCES story(id)
        ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;
