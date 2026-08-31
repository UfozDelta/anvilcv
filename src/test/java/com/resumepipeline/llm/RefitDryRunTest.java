package com.resumepipeline.llm;

import com.resumepipeline.config.GenerationConfig;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.progress.ProgressLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Dry run of {@code BulletService.refit} for one project: same off-band selection, same
 * LLM refit call, same accept/reject rules -- but nothing is written back. Exists so the
 * first refit against the V24 bands can be eyeballed before 48 stored rows are rewritten.
 *
 * <p>Reads the database directly rather than booting Spring, so Flyway does not run and
 * no migration is applied as a side effect.
 *
 * <pre>REFIT_DRY=1 DB_URL=... DB_USER=... DB_PASSWORD=... GEMINI_API_KEY=... mvn test -Dtest=RefitDryRunTest</pre>
 */
class RefitDryRunTest {

    private record Row(UUID id, String text, String status) {}

    @Test
    @EnabledIfEnvironmentVariable(named = "REFIT_DRY", matches = "1")
    void proposeRefitsForTheWorstProject() throws Exception {
        GenerationConfig cfg = new GenerationConfig();   // V24 shipped defaults
        String url = System.getenv("DB_URL"), user = System.getenv("DB_USER"), pw = System.getenv("DB_PASSWORD");

        UUID projectId = null;
        String projectName = null;
        List<Row> all = new ArrayList<>();

        try (Connection c = DriverManager.getConnection(url, user, pw)) {
            // The project with the most bullets -- the biggest single sample of rewrites.
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("""
                         SELECT b.project_id, p.name, count(*) AS n
                         FROM bullet b JOIN project p ON p.id = b.project_id
                         GROUP BY b.project_id, p.name ORDER BY n DESC LIMIT 1""")) {
                if (rs.next()) {
                    projectId = (UUID) rs.getObject(1);
                    projectName = rs.getString(2);
                }
            }
            if (projectId == null) { System.out.println("no bullets"); return; }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id, text, status FROM bullet WHERE project_id = ? ORDER BY created_at")) {
                ps.setObject(1, projectId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) all.add(new Row((UUID) rs.getObject(1), rs.getString(2), rs.getString(3)));
                }
            }
        }

        List<Row> eligible = all.stream().filter(b -> !"APPROVED".equals(b.status())).toList();
        List<Row> offBand = eligible.stream()
                .filter(b -> BulletTextRules.decide(BulletTextRules.charCount(b.text()), cfg)
                        != BulletTextRules.Decision.KEPT)
                .toList();

        System.out.printf("%nproject: %s%n%d bullets, %d eligible, %d off-band%n",
                projectName, all.size(), eligible.size(), offBand.size());
        if (offBand.isEmpty()) return;

        GenerationConfigService cs = mock(GenerationConfigService.class);
        when(cs.get(any())).thenReturn(cfg);
        var llm = new GoogleLlmClient(System.getenv("GEMINI_API_KEY"), "gemini-2.5-flash",
                "gemini-2.5-flash", "gemini-2.5-flash-lite", cs);

        var res = llm.refitBullets(new LlmClient.RefitRequest(UUID.randomUUID(), offBand.stream()
                .map(b -> new LlmClient.BulletToRefit(b.id().toString(), b.text())).toList()),
                ProgressLog.noOp(), new TokenAccumulator());

        Map<String, Row> byId = new LinkedHashMap<>();
        offBand.forEach(b -> byId.put(b.id().toString(), b));
        int maxBold = BulletTextRules.maxBoldSpans(cfg);
        int ok = 0;

        for (var r : res.bullets()) {
            Row b = byId.get(r.id());
            if (b == null) continue;
            String text = BulletTextRules.capBoldSpans(BulletTextRules.ensureTerminalPeriod(r.text()), maxBold);
            int before = BulletTextRules.charCount(b.text()), after = BulletTextRules.charCount(text);
            var decision = BulletTextRules.decide(after, cfg);
            int linesBefore = BulletTextRules.estimatedLines(b.text());
            int linesAfter = BulletTextRules.estimatedLines(text);
            boolean inBand = decision == BulletTextRules.Decision.KEPT;
            // Mirrors rejectRefit: off-band is fine when it costs fewer rendered lines.
            boolean accepted = inBand || linesAfter < linesBefore;
            if (accepted) ok++;
            System.out.printf("%n%s  %dc/%dL -> %dc/%dL  %s%n",
                    inBand ? "OK    " : accepted ? "ACCEPT" : "REJECT",
                    before, linesBefore, after, linesAfter,
                    inBand ? "" : decision + (accepted ? " but fewer lines" : ""));
            System.out.println("  before: " + b.text());
            System.out.println("  after:  " + text);
        }
        System.out.printf("%n%d/%d proposed rewrites would be accepted. NOTHING WRITTEN.%n", ok, offBand.size());
    }
}
