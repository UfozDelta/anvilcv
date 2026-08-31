-- Three context columns the extractor already produces but had no home for.
-- Previously folded into hardest_problem / ownership, where the LLM read them
-- under the wrong label. No backfill: re-running the extractor is cheaper and
-- more accurate than splitting the folded text apart with a regex.
ALTER TABLE project ADD COLUMN technical_decisions TEXT;
ALTER TABLE project ADD COLUMN user_impact         TEXT;
ALTER TABLE project ADD COLUMN security_posture    TEXT;
