-- Four lenses only: ai-ml, backend, data, general. Every other slug (frontend, security, devops,
-- systems, comms, and anything unknown) becomes general. Order inside story.lenses is kept,
-- duplicates collapse onto their first occurrence.
UPDATE bullet
   SET category = 'general'
 WHERE category NOT IN ('ai-ml', 'backend', 'data', 'general');

UPDATE story s
   SET lenses = COALESCE((
         SELECT array_agg(d.lens ORDER BY d.ord)
           FROM (
             SELECT DISTINCT ON (m.lens) m.lens, m.ord
               FROM (
                 SELECT CASE WHEN t.x IN ('ai-ml', 'backend', 'data', 'general') THEN t.x ELSE 'general' END AS lens,
                        t.ord
                   FROM unnest(s.lenses) WITH ORDINALITY AS t(x, ord)
               ) m
              ORDER BY m.lens, m.ord
           ) d
       ), '{}')
 WHERE EXISTS (
         SELECT 1 FROM unnest(s.lenses) AS x
          WHERE x NOT IN ('ai-ml', 'backend', 'data', 'general'));
