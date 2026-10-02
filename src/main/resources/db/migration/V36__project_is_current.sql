-- "I currently work here": an explicit flag on experiences instead of inferring it from the free-text dates.
ALTER TABLE project ADD COLUMN is_current BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE project SET is_current = TRUE
 WHERE kind = 'EXPERIENCE' AND dates ~* '(present|current|now|ongoing)\s*$';
