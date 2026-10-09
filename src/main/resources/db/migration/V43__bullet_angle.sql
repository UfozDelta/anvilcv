-- The angle a wording takes (outcome, decision, scale, failure), as the story bank labelled it.
-- Nullable: rows written before this migration, and wordings the model did not label, stay null.
ALTER TABLE bullet
    ADD COLUMN IF NOT EXISTS angle varchar(20);
