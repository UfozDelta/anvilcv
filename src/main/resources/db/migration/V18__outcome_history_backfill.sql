-- V17 started logging outcome_history on create and on every outcome change, and
-- deliberately skipped a backfill. The result: every application that predates V17
-- has no history at all, and the sankey only draws *transitions* (adjacent pairs),
-- so marking such an application once writes a single row and still yields zero
-- edges. The flow diagram stayed empty no matter how many outcomes were marked.
--
-- This backfills the two rows that can be reconstructed from facts already in the
-- application row itself:
--
--   1. Every application began at 'applied' -- that is the column default in V1,
--      not a guess -- at its created_at.
--   2. An application whose outcome is no longer 'applied' must have moved there
--      at some point, even though the move happened before logging existed.
--
-- The STATES are real. The TIMESTAMPS for (2) are synthetic (created_at + 1s,
-- only to order after the baseline row), and any intermediate stage passed through
-- while logging was off is lost -- an application that really went
-- applied -> interview -> offer backfills as a single applied -> offer edge.
-- That loss is accepted knowingly: partial real history beats a permanently blank
-- diagram. Everything recorded from V17 onward has true timestamps.
--
-- Both statements are guarded by NOT EXISTS, so this is insert-only, idempotent,
-- and touches no existing row.

-- 1. baseline: every application starts at 'applied' when it is created
INSERT INTO outcome_history (application_id, outcome, changed_at)
SELECT a.id, 'applied', a.created_at
FROM application a
WHERE NOT EXISTS (
    SELECT 1 FROM outcome_history h
    WHERE h.application_id = a.id AND h.outcome = 'applied'
);

-- 2. current state, for outcomes set before history logging existed
INSERT INTO outcome_history (application_id, outcome, changed_at)
SELECT a.id, a.outcome, a.created_at + interval '1 second'
FROM application a
WHERE a.outcome <> 'applied'
  AND NOT EXISTS (
      SELECT 1 FROM outcome_history h
      WHERE h.application_id = a.id AND h.outcome = a.outcome
  );
