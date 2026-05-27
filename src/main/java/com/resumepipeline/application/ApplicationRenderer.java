package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.profile.Profile;
import com.resumepipeline.profile.ProfileService;
import com.resumepipeline.profile.ProfileService.EducationEntry;
import com.resumepipeline.project.Project;
import com.resumepipeline.render.LatexEscaper;
import com.resumepipeline.render.LatexRenderer;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the LaTeX resume by combining:
 *   - The DB-backed Profile (basics + education + skills)
 *   - The application's selected bullets, split by project.kind:
 *       EXPERIENCE-kind bullets -> Experience section with \resumeSubheading{title}{dates}{company}{location}
 *       PROJECT-kind bullets    -> Projects section with \resumeProjectHeading{name -- emph{tags}}{}
 *
 * Bullet/coursework text supports **markdown bold** which renders as \textbf{...}.
 */
@Component
public class ApplicationRenderer {

    private static final String BOLD_OPEN  = "RPBOLDSTART";
    private static final String BOLD_CLOSE = "RPBOLDEND";
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*", Pattern.DOTALL);

    private final LatexRenderer renderer;
    private final LatexEscaper escaper;
    private final ProfileService profileService;

    public ApplicationRenderer(LatexRenderer renderer, LatexEscaper escaper, ProfileService profileService) {
        this.renderer = renderer;
        this.escaper = escaper;
        this.profileService = profileService;
    }

    public String render(UUID userId, List<Bullet> selectedInOrder, Map<UUID, Project> projectById) {
        Profile p = profileService.get(userId);
        List<EducationEntry> education = profileService.readEducation(p);

        // Partition selected bullets by their project's kind
        List<Bullet> experienceBullets = new ArrayList<>();
        List<Bullet> projectBullets = new ArrayList<>();
        for (Bullet b : selectedInOrder) {
            Project owner = projectById.get(b.getProjectId());
            if (owner != null && owner.getKind() == Project.Kind.EXPERIENCE) {
                experienceBullets.add(b);
            } else {
                projectBullets.add(b);
            }
        }

        return renderer.renderRaw("template/resume.tex", Map.ofEntries(
                Map.entry("NAME",             escapePlain(p.getName())),
                Map.entry("PHONE",            escapePlain(p.getPhone())),
                Map.entry("EMAIL",            escapePlain(p.getEmail())),
                Map.entry("LINKEDIN_HANDLE",  escapePlain(p.getLinkedinHandle())),
                Map.entry("GITHUB_HANDLE",    escapePlain(p.getGithubHandle())),
                Map.entry("PORTFOLIO_LINK",   portfolioLatex(p.getPortfolioUrl())),
                Map.entry("EDUCATION_ITEMS",  renderEducation(education)),
                Map.entry("EXPERIENCE_ITEMS", renderExperience(experienceBullets, projectById)),
                Map.entry("PROJECT_ITEMS",    renderProjects(projectBullets, projectById)),
                Map.entry("SKILLS_LANGUAGES", escapeRich(p.getSkillsLanguages())),
                Map.entry("SKILLS_FRAMEWORKS",escapeRich(p.getSkillsFrameworks())),
                Map.entry("SKILLS_DATABASES", escapeRich(p.getSkillsDatabases())),
                Map.entry("SKILLS_DEVOPS",    escapeRich(p.getSkillsDevops())),
                Map.entry("SKILLS_INTERESTS", escapeRich(p.getSkillsInterests()))
        ));
    }

    private String portfolioLatex(String url) {
        if (url == null || url.isBlank()) return "";
        return " \\hspace{1pt}\n    \\href{" + url + "}{\\underline{Portfolio}}";
    }

    private String renderEducation(List<EducationEntry> entries) {
        StringBuilder sb = new StringBuilder();
        for (EducationEntry e : entries) {
            sb.append("    \\resumeSubheadingUni\n")
              .append("      {").append(escapePlain(e.school())).append("}{").append(escapePlain(e.location())).append("}\n")
              .append("      {").append(escapeRich(e.degree())).append("}{").append(escapePlain(e.dates())).append("}\n")
              .append("      {");
            String cw = e.coursework() == null ? "" : e.coursework().trim();
            if (!cw.isEmpty()) {
                sb.append("\\textbf{Coursework}: ").append(escapeRich(cw));
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    /** Render Experience section from selected EXPERIENCE-kind bullets, grouped by owning project. */
    private String renderExperience(List<Bullet> selected, Map<UUID, Project> projectById) {
        if (selected.isEmpty()) return "";

        LinkedHashMap<UUID, List<Bullet>> grouped = new LinkedHashMap<>();
        for (Bullet b : selected) {
            grouped.computeIfAbsent(b.getProjectId(), k -> new ArrayList<>()).add(b);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<UUID, List<Bullet>> g : grouped.entrySet()) {
            Project p = projectById.get(g.getKey());
            if (p == null) continue;
            String title    = nz(p.getTitle());
            String dates    = nz(p.getDates());
            String company  = nz(p.getCompany());
            String location = nz(p.getLocation());

            sb.append("    \\resumeSubheading\n")
              .append("      {").append(escapeRich(title)).append("}{").append(escapePlain(dates)).append("}\n")
              .append("      {").append(escapePlain(company)).append("}{").append(escapePlain(location)).append("}\n")
              .append("      \\resumeItemListStart\n");
            for (Bullet b : g.getValue()) {
                sb.append("        \\resumeItem{").append(escapeRich(b.getText())).append("}\n");
            }
            sb.append("      \\resumeItemListEnd\n");
        }
        return sb.toString();
    }

    private String renderProjects(List<Bullet> selected, Map<UUID, Project> projectById) {
        if (selected.isEmpty()) return "";

        LinkedHashMap<UUID, List<Bullet>> grouped = new LinkedHashMap<>();
        for (Bullet b : selected) {
            grouped.computeIfAbsent(b.getProjectId(), k -> new ArrayList<>()).add(b);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<UUID, List<Bullet>> g : grouped.entrySet()) {
            Project p = projectById.get(g.getKey());
            String name = p == null ? "Project" : p.getName();
            String tagSummary = g.getValue().stream()
                    .flatMap(b -> Arrays.stream(b.getTags() == null ? new String[0] : b.getTags()))
                    .distinct().limit(6)
                    .reduce((a, b) -> a + ", " + b).orElse("");

            sb.append("      \\resumeProjectHeading\n")
              .append("        {\\textbf{").append(escapePlain(name)).append("}");
            if (!tagSummary.isEmpty()) {
                sb.append(" -- \\emph{").append(escapePlain(tagSummary)).append("}");
            }
            sb.append("}{}\n")
              .append("        \\resumeItemListStart\n");
            for (Bullet b : g.getValue()) {
                sb.append("          \\resumeItem{").append(escapeRich(b.getText())).append("}\n");
            }
            sb.append("        \\resumeItemListEnd\n\n");
        }
        return sb.toString();
    }

    private String escapePlain(String s) {
        return escaper.escape(s);
    }

    String escapeRich(String s) {
        if (s == null || s.isEmpty()) return "";
        Matcher m = BOLD_PATTERN.matcher(s);
        String withSentinels = m.replaceAll(BOLD_OPEN + "$1" + BOLD_CLOSE);
        String escaped = escaper.escape(withSentinels);
        return escaped.replace(BOLD_OPEN, "\\textbf{").replace(BOLD_CLOSE, "}");
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
