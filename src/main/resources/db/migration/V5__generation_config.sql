CREATE TABLE generation_config (
    id               UUID PRIMARY KEY,
    singleton        BOOLEAN NOT NULL DEFAULT TRUE,

    -- word filter
    word_filter_enabled  BOOLEAN NOT NULL DEFAULT TRUE,
    single_line_low      INT     NOT NULL DEFAULT 22,
    single_line_high     INT     NOT NULL DEFAULT 26,
    double_line_low      INT     NOT NULL DEFAULT 42,
    double_line_high     INT     NOT NULL DEFAULT 50,
    dead_zone_low        INT     NOT NULL DEFAULT 27,
    dead_zone_high       INT     NOT NULL DEFAULT 40,
    min_word_floor       INT     NOT NULL DEFAULT 12,

    -- generation tuning
    temperature          DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    bold_density         TEXT    NOT NULL DEFAULT 'LIGHT',
    tone                 TEXT    NOT NULL DEFAULT 'NEUTRAL',
    action_verb_style    TEXT    NOT NULL DEFAULT 'TECHNICAL',

    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT generation_config_singleton UNIQUE (singleton)
);

INSERT INTO generation_config (id, singleton)
VALUES (gen_random_uuid(), TRUE);
