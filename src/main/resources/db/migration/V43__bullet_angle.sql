-- The angle a wording takes (outcome, decision, scale, failure), as the story bank labelled it.
-- Nullable: rows written before this migration, and wordings the model did not label, stay null.
ALTER TABLE bullet
    ADD COLUMN IF NOT EXISTS angle varchar(20);

-- The judge's short good/bad note for a kept wording. Null for rows written before this migration,
-- and for wordings the judge did not score.
ALTER TABLE bullet
    ADD COLUMN IF NOT EXISTS judge_note text;
