package com.resumepipeline.application;

import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.profile.Profile;
import com.resumepipeline.profile.ProfileService;
import com.resumepipeline.project.Project;
import com.resumepipeline.render.LatexEscaper;
import com.resumepipeline.render.LatexRenderer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the %%SECTION:HEADER%% / %%SECTION:SKILLS_BLOCK%% markers added to
 * resume.tex: renderSnippet blanks both gate keys to drop those blocks, and the real
 * resume must keep them even when the profile leaves the fields empty.
 *
 * <p>profileService is null — renderSnippet never reads it.
 */
class ApplicationRendererSnippetTest {

    private final ApplicationRenderer renderer =
            new ApplicationRenderer(new LatexRenderer(new LatexEscaper()), new LatexEscaper(), null);

    private static Project project(Project.Kind kind) {
        Project p = new Project();
        p.setKind(kind);
        p.setName("Foundify");
        p.setTitle("Founding Engineer");
        p.setCompany("Foundify Inc");
        setId(p, UUID.randomUUID());
        return p;
    }

    @Test void snippetDropsHeaderEducationAndSkillsButKeepsBullets() {
        Project p = project(Project.Kind.PROJECT);
        Bullet b = new Bullet(p.getId(), "Shipped **64K** rows", new String[]{"backend"}, "general");

        String tex = renderer.renderSnippet(java.util.List.of(b), Map.of(p.getId(), p));

        assertFalse(tex.contains("\\section{Education}"), "education should be dropped");
        assertFalse(tex.contains("\\section{Technical Skills}"), "skills should be dropped");
        assertFalse(tex.contains("\\Huge"), "name header should be dropped");
        assertTrue(tex.contains("\\section{Projects}"), "projects section should survive");
        assertTrue(tex.contains("Shipped \\textbf{64K} rows"), "bullet text should render");
    }

    @Test void snippetRendersExperienceHeadingForExperienceKind() {
        Project p = project(Project.Kind.EXPERIENCE);
        Bullet b = new Bullet(p.getId(), "Led migration", new String[0], "general");

        String tex = renderer.renderSnippet(java.util.List.of(b), Map.of(p.getId(), p));

        assertTrue(tex.contains("\\resumeSubheading"), "experience heading macro");
        assertTrue(tex.contains("Foundify Inc"), "company should appear in the heading");
        assertFalse(tex.contains("\\section{Projects}"), "no projects section for experience-only");
    }

    // Application render puts JD-matched stack terms first, before the four-term cap
    // (alias-aware: "k8s" names Kubernetes); the bullet preview keeps the author's order.
    @Test void applicationRenderLeadsHeadingWithJdKeywordsSnippetDoesNot() {
        Project p = project(Project.Kind.PROJECT);
        p.setTechStack("Java, React, Kafka, Redis, Kubernetes");
        Bullet b = new Bullet(p.getId(), "Shipped it", new String[0], "general");
        UUID user = UUID.randomUUID();
        ProfileService profiles = org.mockito.Mockito.mock(ProfileService.class);
        Profile profile = new Profile();
        org.mockito.Mockito.when(profiles.get(user)).thenReturn(profile);
        org.mockito.Mockito.when(profiles.readEducation(profile)).thenReturn(List.of());
        ApplicationRenderer full = new ApplicationRenderer(
                new LatexRenderer(new LatexEscaper()), new LatexEscaper(), profiles);

        String app = full.render(user, List.of(b), Map.of(p.getId(), p), List.of(), Map.of(), List.of("k8s"));
        String snippet = renderer.renderSnippet(List.of(b), Map.of(p.getId(), p));

        assertTrue(app.contains("\\emph{Kubernetes, Java, React, Kafka}"), app);
        assertTrue(snippet.contains("\\emph{Java, React, Kafka, Redis}"), snippet);
    }

    private static void setId(Object entity, UUID id) {
        try {
            Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
