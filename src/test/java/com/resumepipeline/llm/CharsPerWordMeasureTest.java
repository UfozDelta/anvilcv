package com.resumepipeline.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only measurement of the real {@link BulletTextRules#CHARS_PER_WORD} ratio against the
 * shipped bullet corpus. The constant converts the word-based config bands into the character
 * bands the filter actually enforces, so if it is wrong the "roughly N words" hint in the
 * generation prompt asks for a length the filter then rejects.
 *
 * <p>Off by default -- needs the live database. Run with:
 * <pre>MEASURE_CPW=1 DB_URL=... DB_USER=... DB_PASSWORD=... mvn test -Dtest=CharsPerWordMeasureTest</pre>
 */
class CharsPerWordMeasureTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "MEASURE_CPW", matches = "1")
    void measureRatioAcrossCorpus() throws Exception {
        List<String> texts = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(
                System.getenv("DB_URL"), System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT text FROM bullet")) {
            while (rs.next()) texts.add(rs.getString(1));
        }

        if (texts.isEmpty()) {
            System.out.println("no bullets in corpus");
            return;
        }

        List<Double> ratios = new ArrayList<>();
        int oneLine = 0, twoLine = 0, threePlus = 0, maxChars = 0;
        for (String t : texts) {
            int chars = BulletTextRules.charCount(t);
            int words = BulletTextRules.wordCount(t);
            if (words > 0) ratios.add(chars / (double) words);
            int lines = BulletTextRules.estimatedLines(t);
            if (lines == 1) oneLine++; else if (lines == 2) twoLine++; else threePlus++;
            maxChars = Math.max(maxChars, chars);
        }
        ratios.sort(Double::compare);

        double mean = ratios.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        System.out.printf("%nbullets=%d  mean=%.2f  p50=%.2f  p90=%.2f  min=%.2f  max=%.2f%n",
                texts.size(), mean, pct(ratios, 50), pct(ratios, 90), ratios.get(0),
                ratios.get(ratios.size() - 1));
        System.out.printf("configured CHARS_PER_WORD=%.1f%n", BulletTextRules.CHARS_PER_WORD);
        System.out.printf("rendered lines: 1-line=%d  2-line=%d  3+line=%d  longest=%d chars%n",
                oneLine, twoLine, threePlus, maxChars);
        System.out.printf("chars-per-line=%d, so a 2-line ceiling is %d chars%n",
                BulletTextRules.CHARS_PER_LINE, 2 * BulletTextRules.CHARS_PER_LINE);
    }

    private static double pct(List<Double> sorted, int p) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1));
    }
}
