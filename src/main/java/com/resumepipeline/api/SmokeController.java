package com.resumepipeline.api;

import com.resumepipeline.render.LatexRenderer;
import com.resumepipeline.render.PdfCompiler;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Day 1 smoke endpoints. Delete once real controllers exist.
 *   GET /api/smoke/ping  -> "pong"
 *   GET /api/smoke/pdf   -> sample resume PDF using the real template
 */
@RestController
@RequestMapping("/api/smoke")
public class SmokeController {

    private final LatexRenderer renderer;
    private final PdfCompiler compiler;

    public SmokeController(LatexRenderer renderer, PdfCompiler compiler) {
        this.renderer = renderer;
        this.compiler = compiler;
    }

    @GetMapping("/ping")
    public String ping() { return "pong"; }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf() {
        String education =
            "    \\resumeSubheadingUni\n" +
            "      {University of Toronto}{Toronto, ON}\n" +
            "      {Honours BSc in Computer Science}{Sep. 2022 - Apr. 2026}\n" +
            "      {\\textbf{Coursework}: Distributed Systems, Databases, Operating Systems, " +
            "Machine Learning, Algorithms, Computer Networks}\n";

        String experience =
            "    \\resumeSubheading\n" +
            "      {Software Engineering Intern}{May 2025 - Aug. 2025}\n" +
            "      {Example Corp}{Toronto, ON}\n" +
            "      \\resumeItemListStart\n" +
            "        \\resumeItem{Built a Spring Boot service that tailors resumes to job descriptions " +
            "using Gemini structured output, reducing application turnaround from 30 minutes to under 1 minute.}\n" +
            "        \\resumeItem{Wrote a tested LaTeX escaper handling \\% \\& \\_ \\# \\$ \\textasciitilde{} " +
            "\\textasciicircum{} and Unicode quotes/dashes, surviving arbitrary LLM-generated text.}\n" +
            "        \\resumeItem{Deployed on Render with a Docker image that pre-warms the tectonic " +
            "package cache during build, cutting first-compile latency from 60s to under 2s.}\n" +
            "      \\resumeItemListEnd\n";

        String projects =
            "      \\resumeProjectHeading\n" +
            "        {\\textbf{resume-pipeline} -- \\emph{Java, Spring Boot, React, Postgres, Gemini, tectonic}}{2026 - Present}\n" +
            "        \\resumeItemListStart\n" +
            "          \\resumeItem{Full-stack tool that ingests projects, generates bullet points via " +
            "Gemini 2.5 Pro, ranks them against a pasted JD, and compiles a tailored resume PDF.}\n" +
            "          \\resumeItem{Designed a two-pass LaTeX escaper using a sentinel-swap for backslashes " +
            "so escape sequences don't cascade; 21 unit tests covering every special-char permutation.}\n" +
            "          \\resumeItem{Deployed on Render + Neon + Vercel with Spring Security session cookies, " +
            "Flyway-managed schema, and JSONB-stored bullet rankings with rationale.}\n" +
            "        \\resumeItemListEnd\n";

        String tex = renderer.renderRaw("template/resume.tex", Map.ofEntries(
            Map.entry("NAME", "Felix Test"),
            Map.entry("PHONE", "555-555-0100"),
            Map.entry("EMAIL", "felix@example.com"),
            Map.entry("LINKEDIN_HANDLE", "felix-test"),
            Map.entry("GITHUB_HANDLE", "felix"),
            Map.entry("EDUCATION_ITEMS", education),
            Map.entry("EXPERIENCE_ITEMS", experience),
            Map.entry("PROJECT_ITEMS", projects),
            Map.entry("PORTFOLIO_LINK",    ""),
            Map.entry("SKILLS_LANGUAGES",  "Java, TypeScript, Python, SQL, C++"),
            Map.entry("SKILLS_FRAMEWORKS", "Spring Boot, React, Next.js, Node.js, PyTorch"),
            Map.entry("SKILLS_DATABASES",  "PostgreSQL, Redis, ChromaDB, RAG"),
            Map.entry("SKILLS_DEVOPS",     "Docker, Git, GitHub Actions, CI/CD"),
            Map.entry("SKILLS_INTERESTS",  "OpenAI API, LangChain, MCP, Stripe Webhooks")
        ));

        PdfCompiler.Result r = compiler.compile(tex);
        if (!r.success()) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("tectonic failed: " + r.error() + "\n\n" + r.log()).getBytes());
        }

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDispositionFormData("inline", "smoke.pdf");
        return new ResponseEntity<>(r.pdf(), h, 200);
    }
}
