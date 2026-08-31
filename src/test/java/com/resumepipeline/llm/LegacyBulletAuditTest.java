package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only audit of stored bullets against the V24 bands. V24 recalibrated generation;
 * rows written before it are untouched, and refit skips APPROVED ones -- so this reports
 * how much of the corpus is off-band and how much of that a refit run could actually fix.
 *
 * <pre>AUDIT_BULLETS=1 DB_URL=... DB_USER=... DB_PASSWORD=... mvn test -Dtest=LegacyBulletAuditTest</pre>
 */
class LegacyBulletAuditTest {

    private record Row(String text, String status, String category) {}

    @Test
    @EnabledIfEnvironmentVariable(named = "AUDIT_BULLETS", matches = "1")
    void auditStoredBulletsAgainstCurrentBands() throws Exception {
        List<Row> rows = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(
                System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT text, status, category FROM bullet")) {
            while (rs.next()) rows.add(new Row(rs.getString(1), rs.getString(2), rs.getString(3)));
        }

        GenerationConfig cfg = new GenerationConfig();   // V24 shipped defaults
        Map<String, int[]> byStatus = new LinkedHashMap<>();   // status -> {total, offBand, threePlus}
        Map<BulletTextRules.Decision, Integer> why = new LinkedHashMap<>();
        int worstChars = 0;
        String worst = "";

        for (Row r : rows) {
            int chars = BulletTextRules.charCount(r.text());
            var d = BulletTextRules.decide(chars, cfg);
            int lines = BulletTextRules.estimatedLines(r.text());
            int[] acc = byStatus.computeIfAbsent(r.status(), k -> new int[3]);
            acc[0]++;
            if (d != BulletTextRules.Decision.KEPT) {
                acc[1]++;
                why.merge(d, 1, Integer::sum);
            }
            if (lines >= 3) acc[2]++;
            if (chars > worstChars) { worstChars = chars; worst = r.text(); }
        }

        System.out.printf("%n%-10s %7s %9s %10s%n", "STATUS", "TOTAL", "OFF-BAND", "3+ LINES");
        byStatus.forEach((s, a) -> System.out.printf("%-10s %7d %9d %10d%n", s, a[0], a[1], a[2]));

        int refittable = byStatus.entrySet().stream()
                .filter(e -> !"APPROVED".equals(e.getKey()))
                .mapToInt(e -> e.getValue()[1]).sum();
        int stuck = byStatus.getOrDefault("APPROVED", new int[3])[1];

        System.out.printf("%noff-band reasons: %s%n", why);
        System.out.printf("refit can fix: %d   refit will skip (APPROVED): %d%n", refittable, stuck);
        System.out.printf("longest stored bullet: %d chars (%d rendered lines)%n",
                worstChars, BulletTextRules.estimatedLines(worst));
    }
}
