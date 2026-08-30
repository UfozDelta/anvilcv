package com.resumepipeline.render;

import com.resumepipeline.application.ApplicationRenderer;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.project.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compiles the real template and checks that an ATS can still read the result.
 *
 * <p><b>Why this exists.</b> {@code resume.tex} lays each heading out as a two-column
 * {@code tabular*} — employer on the left, dates right-aligned against the margin. A PDF
 * carries no table structure, only positioned glyphs, so whether those two halves come
 * back out attached to each other is a property of the extractor, not of the document.
 * Layout-analysis extractors can read the wide gap as a column boundary and emit the
 * whole left column first, which detaches every date range from its employer and lands
 * it beside the *next* one. That is a wrong-facts parse: right job, wrong dates.
 *
 * <p>Measured on 2026-08-29, the current template survives PDFBox, pypdf and (mostly)
 * poppler, and fails only under pdfminer.six's default {@code LAParams}. PDFBox is the
 * one that decides the question — Apache Tika wraps it and the large ATS vendors are
 * Java shops — so it is what this test asserts against. Narrowing the gap does not help:
 * pdfminer splits at every width down to 0.40\textwidth, so its behaviour is a property
 * of its defaults rather than something the template can be tuned away from.
 *
 * <p><b>What it guards.</b> Any future change to the heading macros, the page geometry,
 * or the fonts. Run it after editing {@code resume.tex}; if it goes red, the new layout
 * has broken date-to-employer binding for every ATS that behaves like PDFBox.
 *
 * <p><b>Possible feature later.</b> The same three steps — render, compile, extract, and
 * check the facts survived — could run inside the application pipeline against the real
 * generated PDF, and surface a parse warning on the application page next to the ATS
 * keyword panel. That would catch the failure for a user's actual data (an unusually long
 * job title, say, colliding with the dates column) rather than only for this fixture.
 * {@code ApplicationService} already reconstructs the rendered text in Java to score ATS
 * keywords; reading it back out of the compiled PDF instead would make that score reflect
 * what a parser genuinely sees.
 *
 * <p>Skipped unless tectonic is present, since it forks the real binary.
 */
class ResumePdfParseTest {

    /** Same default as {@code PdfCompiler}; override to match application-local.yml. */
    private static final String TECTONIC =
            System.getProperty("tectonic.binary", System.getenv().getOrDefault("TECTONIC_BINARY", "tectonic"));

    static boolean tectonicAvailable() {
        if (Path.of(TECTONIC).isAbsolute()) return Files.isExecutable(Path.of(TECTONIC));
        try {
            Process p = new ProcessBuilder(TECTONIC, "--version").redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private final ApplicationRenderer renderer =
            new ApplicationRenderer(new LatexRenderer(new LatexEscaper()), new LatexEscaper(), null);

    @Test
    @EnabledIf("tectonicAvailable")
    void datesStayAttachedToTheirEmployer() {
        // Two experience entries with distinct, unambiguous values. If the extractor reads
        // the heading as two columns, entry one's dates surface beside entry two's title.
        Project acme = experience("Anvil Platform", "Senior Backend Engineer",
                "Acme Robotics", "Boston, MA", "Jun 2023 -- Present");
        Project tess = experience("Ingest", "Backend Engineer",
                "Tessellate Data", "Remote", "Jul 2021 -- Jun 2023");

        List<Bullet> bullets = List.of(
                bullet(acme, "Cut p99 checkout latency from **1.8s** to **240ms** by batching the read path."),
                bullet(acme, "Moved **64,000** nightly listings onto Postgres partitions, halving index bloat."),
                bullet(tess, "Built the ingest pipeline now carrying **12M** events per day across six regions."));

        // renderSnippet covers the heading macros without needing a ProfileService.
        String tex = renderer.renderSnippet(bullets, Map.of(acme.getId(), acme, tess.getId(), tess));

        PdfCompiler.Result result = new PdfCompiler(TECTONIC, 60).compile(tex);
        assertTrue(result.success(), () -> "tectonic failed: " + result.error() + "\n" + result.log());
        assertNotNull(result.pdf());

        String text = extract(result.pdf());
        assertLinesAdjacent(text, "Acme Robotics", "Jun 2023");
        assertLinesAdjacent(text, "Tessellate Data", "Jul 2021");
    }

    /**
     * Negative control. The check above only means something if it rejects the column-order
     * layout it exists to catch, so this feeds it exactly that shape: every employer first,
     * then every date range. Without this, a change that made the assertion vacuous would
     * leave the test permanently green.
     */
    @Test
    void adjacencyCheckRejectsColumnOrderedText() {
        String columnOrdered = """
                Senior Backend Engineer
                Acme Robotics
                Cut p99 checkout latency by batching the read path.
                Jun 2023 - Present
                Boston, MA
                """;
        assertThrows(AssertionError.class,
                () -> assertLinesAdjacent(columnOrdered, "Acme Robotics", "Jun 2023"));
    }

    /**
     * A parser binds a date range to an employer by proximity, so the two must still come
     * out within a line of each other. They land on the same line when the heading reads
     * in row order, and pages apart when it reads in column order.
     */
    private static void assertLinesAdjacent(String text, String employer, String dates) {
        List<String> lines = text.lines().map(String::strip).filter(s -> !s.isEmpty()).toList();
        int e = indexOfLineContaining(lines, employer);
        int d = indexOfLineContaining(lines, dates);
        if (e < 0 || d < 0 || Math.abs(d - e) > 1) {
            fail("'" + employer + "' (line " + e + ") and '" + dates + "' (line " + d
                    + ") are not adjacent in the extracted text — a parser would bind the wrong"
                    + " dates to this employer.\nExtracted:\n" + String.join("\n", lines));
        }
    }

    private static int indexOfLineContaining(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) return i;
        }
        return -1;
    }

    private static String extract(byte[] pdf) {
        try (var doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            throw new AssertionError("could not read the generated PDF", e);
        }
    }

    private static Project experience(String name, String title, String company,
                                      String location, String dates) {
        Project p = new Project(UUID.randomUUID(), Project.Kind.EXPERIENCE, name, "desc",
                null, title, company, location, dates);
        setId(p, UUID.randomUUID());
        return p;
    }

    private static Bullet bullet(Project owner, String text) {
        return new Bullet(owner.getId(), text, new String[0], "general");
    }

    /** {@code Project#getId()} is {@code @GeneratedValue} with no setter. */
    private static void setId(Project p, UUID id) {
        try {
            Field f = Project.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(p, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
