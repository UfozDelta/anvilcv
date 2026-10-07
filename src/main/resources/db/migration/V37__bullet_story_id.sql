-- Groups the wordings of one story written in one bank build (one per lens), so selection
-- never puts two wordings of the same work on a resume. Null for bullets written before
-- story generation and for hand-added ones: each such row is its own story.
ALTER TABLE bullet ADD COLUMN story_id uuid;
