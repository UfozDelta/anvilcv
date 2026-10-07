-- Admin-only bullet eval: frozen snapshots and dry-run generations of bullet banks, compared
-- side by side. Never joined to bullet; items is the whole set, read and written in one piece.
CREATE TABLE eval_set (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    label      TEXT NOT NULL UNIQUE,
    source     TEXT NOT NULL,                 -- BASELINE | GENERATED
    note       TEXT,                          -- git sha / what changed in the generator
    status     TEXT NOT NULL,                 -- RUNNING | DONE | FAILED
    error      TEXT,
    items      JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
