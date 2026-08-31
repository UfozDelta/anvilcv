-- V21 set these word bands assuming BulletTextRules.CHARS_PER_WORD = 5.4. Measured
-- against the real bullet corpus (n=84) that ratio is 7.41 mean / 7.38 median -- the
-- technical vocabulary these bullets are made of ("PostgreSQL", "anti-hallucination",
-- "kind-floor eviction") runs far longer than 5.4 characters a word.
--
-- The character bands were right; only the word numbers were wrong. The generation
-- prompt quotes both ("N to M characters (roughly P to Q words)"), so a model aiming
-- at the old 32-37 words landed near 270 characters -- three rendered lines, cut as
-- TOO_LONG after we had already paid for the output tokens.
--
-- These bands re-derive the same character targets at the measured ratio:
--   one line  11-13 words -> ~81-96 chars  (<= 105, one line)
--   two lines 23-27 words -> ~170-200 chars (<= 210, two lines, never three)
--
-- Only rows still holding every one of V21's defaults are touched, so a user who has
-- deliberately tuned their own bands keeps that tuning.
UPDATE generation_config
SET single_line_low  = 11,
    single_line_high = 13,
    dead_zone_low     = 14,
    dead_zone_high    = 22,
    double_line_low   = 23,
    double_line_high  = 27,
    min_word_floor    = 9
WHERE single_line_low  = 15
  AND single_line_high = 18
  AND dead_zone_low     = 19
  AND dead_zone_high    = 31
  AND double_line_low   = 32
  AND double_line_high  = 37;
