-- Shadow-mode telemetry for BulletLineMeasurer: compares the real tectonic-measured line
-- fit against the char-count band (BulletTextRules.decide) that generation already gates
-- on, without acting on the real measurement yet. One row per bullet compared, agree and
-- disagree alike, so the disagreement RATE is computable, not just a pile of anecdotes.
-- See BulletLineMeasurer's class javadoc: no tectonic binary was available to compile-test
-- its box arithmetic when it was written, so this table is how that gets verified for real
-- before generation is ever allowed to drop a bullet on the strength of it.
create table bullet_measure_diagnostic (
    id uuid primary key default gen_random_uuid(),
    project_id uuid not null,
    user_id uuid not null,
    category text not null,
    bullet_text text not null,
    char_count int not null,
    declared_kept boolean not null,
    measured boolean not null,
    measured_lines int,
    measured_fill double precision,
    agree boolean not null,
    created_at timestamptz not null default now()
);

create index idx_bullet_measure_diagnostic_created_at on bullet_measure_diagnostic (created_at desc);
