-- V24 set these word bands against BulletTextRules.CHARS_PER_LINE = 105 (11pt
-- template). The calibrated template in the SOP measures ~122 characters per
-- rendered line at 10.5pt, so the line targets move to ~98-105 chars for one
-- line and ~170-230 for two, with a hard ceiling of 240 (see
-- BulletTextRules.TWO_LINE_HARD_CEILING_CHARS).
--
-- CHARS_PER_WORD stays at 7.4: that is text density measured from the bullet
-- corpus, not a font metric, so only the word bands need re-deriving:
--   one line  13-14 words -> ~96-104 chars
--   dead zone 15-22 words -> ~111-163 chars
--   two lines 23-31 words -> ~170-229 chars
-- Word bands are integers at 7.4 chars/word, so they bracket the SOP targets
-- within ~6 chars; the 240-char clamp is exact.
--
-- Only rows still holding every one of V24's defaults are touched, so a user
-- who has deliberately tuned their own bands keeps that tuning.
UPDATE generation_config
SET single_line_low  = 13,
    single_line_high = 14,
    dead_zone_low     = 15,
    dead_zone_high    = 22,
    double_line_low   = 23,
    double_line_high  = 31
WHERE single_line_low  = 11
  AND single_line_high = 13
  AND dead_zone_low     = 14
  AND dead_zone_high    = 22
  AND double_line_low   = 23
  AND double_line_high  = 27;
