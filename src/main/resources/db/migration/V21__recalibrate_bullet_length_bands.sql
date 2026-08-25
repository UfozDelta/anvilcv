-- Recalibrate the word-length bands so "single line" and "double line" bullets
-- actually fit one/two rendered lines against resume.tex's 105-char-per-line
-- text block (see BulletTextRules.CHARS_PER_LINE). The old defaults were sized
-- for a narrower textwidth and rendered as 2-3 lines each.
--
-- Only rows still holding every one of the old default values are touched, so
-- a user who has deliberately tuned their own bands keeps their tuning.
UPDATE generation_config
SET single_line_low  = 15,
    single_line_high = 18,
    dead_zone_low     = 19,
    dead_zone_high    = 31,
    double_line_low   = 32,
    double_line_high  = 37
WHERE single_line_low  = 22
  AND single_line_high = 26
  AND dead_zone_low     = 27
  AND dead_zone_high    = 40
  AND double_line_low   = 42
  AND double_line_high  = 50;
