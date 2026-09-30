package com.resumepipeline.github;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RepoMapBuilderTest {

    private static RepoMap map() {
        Map<String, String> f = new LinkedHashMap<>();
        // core: referenced by both api and ui
        f.put("src/core/JobProgressStore.java", """
                package core;
                public class JobProgressStore {
                    public void append(UUID jobId, String line) {}
                }
                """);
        f.put("src/api/ProjectController.java", """
                package api;
                @RestController
                public class ProjectController {
                    private final JobProgressStore store;
                    @GetMapping("/api/projects")
                    public List<Project> list() { return null; }
                    @PostMapping("/api/projects/{id}/submit")
                    public void submit() { store.append(null, "x"); }
                }
                """);
        f.put("web/src/pages/Board.tsx", """
                export function Board() { return JobProgressStore; }
                export const PollInterval = 1500;
                """);
        f.put("worker/tasks.py", """
                @app.route("/health")
                def health(): pass
                class JobProgressStoreClient: pass
                """);
        f.put("src/test/ProjectControllerTest.java", """
                class ProjectControllerTest {
                    @Test void a() {}
                    @Test void b() {}
                }
                """);
        f.put("web/src/pages/Board.test.ts", "it('renders', () => {});\ntest('polls', () => {});\n");
        f.put("db/migration/V1__init.sql", "create table x(id int);");
        f.put("db/migration/V2__more.sql", "alter table x add y int;");
        f.put("package.json", "{\"dependencies\":{\"react\":\"18\",\"d3\":\"7\"},\"devDependencies\":{\"vite\":\"5\"}}");
        f.put("pom.xml", "<dependency>a</dependency><dependency>b</dependency>");
        f.put(".github/workflows/ci.yml", "on: push");
        f.put("README.md", "# App");
        return RepoMapBuilder.build("abc1234", new RepoSnapshot(f, false));
    }

    @Test
    void mostReferencedModuleRanksFirst() {
        RepoMap m = map();
        assertEquals("src/core", m.modules().get(0).path());
        RepoMap.Module api = m.modules().stream().filter(x -> x.path().equals("src/api")).findFirst().orElseThrow();
        assertEquals(List.of("src/core"), api.dependsOn());
    }

    @Test
    void extractsSymbolsAndRoutesPerLanguage() {
        RepoMap m = map();
        RepoMap.Module api = m.modules().stream().filter(x -> x.path().equals("src/api")).findFirst().orElseThrow();
        assertTrue(api.symbols().contains("class ProjectController"));
        assertTrue(api.symbols().stream().anyMatch(s -> s.startsWith("public List<Project> list()")), api.symbols().toString());
        assertEquals(List.of("GET /api/projects", "POST /api/projects/{id}/submit"), api.routes());

        RepoMap.Module web = m.modules().stream().filter(x -> x.path().equals("web/src/pages")).findFirst().orElseThrow();
        assertTrue(web.symbols().containsAll(List.of("function Board", "const PollInterval")));
        RepoMap.Module py = m.modules().stream().filter(x -> x.path().equals("worker")).findFirst().orElseThrow();
        assertEquals(List.of("ROUTE /health"), py.routes());
    }

    @Test
    void countsFactsFromTheCode() {
        Map<String, String> facts = new LinkedHashMap<>();
        for (RepoMap.Fact f : map().facts()) facts.put(f.label(), f.value());
        assertEquals("4", facts.get("test cases"));           // 2 @Test + it( + test(
        assertEquals("3", facts.get("API endpoints"));
        assertEquals("2", facts.get("database migrations"));
        assertEquals("5", facts.get("declared dependencies")); // 3 npm + 2 maven
        assertEquals("1", facts.get("CI workflows"));
        assertNotNull(facts.get("lines of code"));
    }

    @Test
    void testDetection() {
        assertTrue(RepoMapBuilder.isTest("src/test/java/FooTest.java"));
        assertTrue(RepoMapBuilder.isTest("web/Board.test.ts"));
        assertTrue(RepoMapBuilder.isTest("pkg/test_io.py"));
        assertTrue(RepoMapBuilder.isTest("pkg/io_test.go"));
        assertFalse(RepoMapBuilder.isTest("src/main/Testimonial.java"));
    }
}
