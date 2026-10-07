package com.resumepipeline.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.application.ApplicationService;
import com.resumepipeline.bullet.Bullet;
import com.resumepipeline.bullet.BulletRepository;
import com.resumepipeline.config.GenerationConfigService;
import com.resumepipeline.llm.CategoryLenses;
import com.resumepipeline.llm.KeywordScorer;
import com.resumepipeline.obs.LogText;
import com.resumepipeline.progress.ProgressLog;
import com.resumepipeline.project.Project;
import com.resumepipeline.project.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Admin bullet eval: freeze a bank, dry-run the current generator into a second one, compare.
 * Reads the bullet/project/application tables; writes only eval_set.
 */
@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final EvalSetRepository sets;
    private final ProjectRepository projects;
    private final BulletRepository bullets;
    private final ApplicationRepository applications;
    private final GenerationConfigService configService;
    private final DryRunGenerator generator;
    private final ObjectMapper mapper = new ObjectMapper();

    public EvalService(EvalSetRepository sets, ProjectRepository projects, BulletRepository bullets,
                       ApplicationRepository applications, GenerationConfigService configService,
                       DryRunGenerator generator) {
        this.sets = sets;
        this.projects = projects;
        this.bullets = bullets;
        this.applications = applications;
        this.configService = configService;
        this.generator = generator;
    }

    /** A run still RUNNING at boot lost its thread to the restart; say so instead of spinning forever. */
    @EventListener(ApplicationReadyEvent.class)
    public void failOrphanedRuns() {
        List<EvalSet> orphans = sets.findByStatus(EvalSet.RUNNING);
        orphans.forEach(s -> { s.setStatus(EvalSet.FAILED); s.setError("interrupted by restart"); });
        sets.saveAll(orphans);
    }

    public List<EvalSet> list() {
        return sets.findAllByOrderByCreatedAtDesc();
    }

    public List<EvalItem> items(EvalSet s) {
        try {
            return mapper.readValue(s.getItems(), new TypeReference<List<EvalItem>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Corrupt items in eval set " + s.getLabel(), e);
        }
    }

    /**
     * Copies this user's live bullet bank (every status) into a new BASELINE set. Reads the
     * bullet table only; the snapshot lives in eval_set, never in the repo.
     */
    public EvalSet snapshotBank(UUID userId) {
        List<EvalItem> items = new ArrayList<>();
        for (Project p : projects.findAllByUserIdOrderByCreatedAtDesc(userId)) {
            for (Bullet b : bullets.findByProjectIdOrderByCreatedAtAsc(p.getId())) {
                items.add(new EvalItem(p.getId(), p.getName(), p.getKind().name(), b.getCategory(), b.getStatus(),
                        Arrays.asList(b.getTags()), b.getText(), b.getStoryId()));
            }
        }
        if (items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No bullets to snapshot");
        EvalSet s = new EvalSet("bank-" + Instant.now().toString().substring(0, 19).replace(':', '-'),
                EvalSet.BASELINE, "bullet bank snapshot " + LocalDate.now(), EvalSet.DONE);
        s.setItems(write(items));
        return sets.save(s);
    }

    /**
     * Starts a dry-run generation for these projects, each over the lenses the reference set
     * holds for it, so the two sets cover the same ground. Returns at once; the set flips to
     * DONE or FAILED when the background run ends. Spends real LLM tokens (logged as
     * eval_generation under the project owner).
     */
    public EvalSet startGeneration(UUID referenceSetId, List<UUID> projectIds, String note) {
        if (projectIds == null || projectIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick at least one project");
        }
        EvalSet reference = get(referenceSetId);
        Map<UUID, List<String>> lensesByProject = items(reference).stream().collect(Collectors.groupingBy(
                EvalItem::projectId, LinkedHashMap::new,
                Collectors.mapping(EvalItem::category, Collectors.collectingAndThen(Collectors.toList(),
                        cats -> cats.stream().filter(CategoryLenses.LENSES::containsKey).distinct().toList()))));
        Map<UUID, Project> byId = projects.findByIdIn(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));
        for (UUID id : projectIds) {
            if (!byId.containsKey(id)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown project " + id);
            if (lensesByProject.getOrDefault(id, List.of()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Reference set has no lens bullets for " + byId.get(id).getName());
            }
        }
        EvalSet run = sets.save(new EvalSet("gen-" + Instant.now().toString().substring(0, 19).replace(':', '-'),
                EvalSet.GENERATED, note, EvalSet.RUNNING));
        Thread.ofVirtual().name("eval-" + run.getId()).start(() ->
                generate(run, projectIds.stream().map(byId::get).toList(), lensesByProject));
        return run;
    }

    void generate(EvalSet run, List<Project> targets, Map<UUID, List<String>> lensesByProject) {
        List<EvalItem> out = new ArrayList<>();
        try {
            for (Project p : targets) {
                List<String> lenses = lensesByProject.get(p.getId());
                log.info("EVAL_GEN set={} project={} lenses={}", run.getLabel(), p.getName(), lenses);
                for (Bullet b : generator.generate(p.getUserId(), p.getId(), lenses, ProgressLog.noOp())) {
                    out.add(new EvalItem(p.getId(), p.getName(), p.getKind().name(), b.getCategory(), b.getStatus(),
                            Arrays.asList(b.getTags()), b.getText(), b.getStoryId()));
                }
            }
            run.setItems(write(out));
            run.setStatus(EvalSet.DONE);
        } catch (RuntimeException e) {
            log.warn("EVAL_GEN set={} failed: {}", run.getLabel(), e.getMessage(), e);
            run.setItems(write(out));
            run.setStatus(EvalSet.FAILED);
            run.setError(LogText.abbreviate(String.valueOf(e.getMessage()), 500));
        }
        sets.save(run);
    }

    public void delete(UUID id) {
        EvalSet s = get(id);
        // The run's thread saves the set when it finishes and would resurrect it.
        if (EvalSet.RUNNING.equals(s.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Set is still generating");
        }
        sets.delete(s);
    }

    /** Metrics for A and B, overall and per project, plus the no-LLM job-match replay. */
    public Map<String, Object> compare(UUID aId, UUID bId) {
        EvalSet a = get(aId), b = get(bId);
        List<EvalItem> ia = items(a), ib = items(b);
        Set<UUID> projectIds = Stream.concat(ia.stream(), ib.stream()).map(EvalItem::projectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Project> live = projects.findByIdIn(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> p));
        // Live project text, not a snapshot: both sets are judged against the same source, at
        // the cost of baseline numbers that were backed by since-edited text reading as unbacked.
        Map<UUID, String> sources = live.values().stream()
                .collect(Collectors.toMap(Project::getId, EvalService::sourceText));
        // One owner in practice; the first one's bands are the yardstick.
        var cfg = configService.get(live.values().stream().map(Project::getUserId).findFirst()
                .orElse(new UUID(0, 0)));

        List<Map<String, Object>> perProject = new ArrayList<>();
        for (UUID pid : projectIds) {
            List<EvalItem> pa = ia.stream().filter(i -> i.projectId().equals(pid)).toList();
            List<EvalItem> pb = ib.stream().filter(i -> i.projectId().equals(pid)).toList();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", pid);
            row.put("name", (pa.isEmpty() ? pb : pa).get(0).projectName());
            row.put("a", side(pa, sources, cfg));
            row.put("b", side(pb, sources, cfg));
            perProject.add(row);
        }

        Set<UUID> owners = live.values().stream().map(Project::getUserId).collect(Collectors.toSet());
        Map<UUID, UUID> ownerOf = live.values().stream().collect(Collectors.toMap(Project::getId, Project::getUserId));
        List<Application> apps = owners.stream()
                .flatMap(u -> applications.findAllByUserIdOrderByCreatedAtDesc(u).stream()).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("a", header(a, EvalMetrics.summarize(ia, sources, cfg)));
        result.put("b", header(b, EvalMetrics.summarize(ib, sources, cfg)));
        result.put("jobMatch", jobMatch(apps, ia, ib, ownerOf));
        result.put("projects", perProject);
        return result;
    }

    /**
     * Replays the deterministic pre-filter (what the ranking LLM gets to pick from) on both
     * sets for every saved application, and reports how many of its JD keywords those
     * candidates cover. Keywords are the application's ats matched + missing lists.
     */
    static Map<String, Object> jobMatch(List<Application> apps, List<EvalItem> a, List<EvalItem> b,
                                        Map<UUID, UUID> ownerOf) {
        List<Map<String, Object>> rows = new ArrayList<>();
        double sumA = 0, sumB = 0;
        for (Application app : apps) {
            Set<String> kw = Stream.concat(Arrays.stream(app.getAtsMatched()), Arrays.stream(app.getAtsMissing()))
                    .map(String::toLowerCase).collect(Collectors.toSet());
            if (kw.isEmpty()) continue;
            List<String> lenses = CategoryLenses.validate(
                    app.getRoleEmphasis() == null ? List.of() : List.of(app.getRoleEmphasis().split("\\+")));
            double ca = coverage(a, app.getUserId(), ownerOf, kw, lenses);
            double cb = coverage(b, app.getUserId(), ownerOf, kw, lenses);
            sumA += ca;
            sumB += cb;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("company", app.getCompany());
            row.put("role", app.getRole());
            row.put("keywords", kw.size());
            row.put("a", ca);
            row.put("b", cb);
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applications", rows.size());
        out.put("meanA", rows.isEmpty() ? 0 : sumA / rows.size());
        out.put("meanB", rows.isEmpty() ? 0 : sumB / rows.size());
        out.put("rows", rows);
        return out;
    }

    /** Same bank the generate pipeline ranks from: no REJECTED, no unreviewed vanity, variants collapsed. */
    static double coverage(List<EvalItem> items, UUID userId, Map<UUID, UUID> ownerOf,
                           Set<String> kw, List<String> lenses) {
        List<Bullet> bank = items.stream()
                .filter(i -> userId.equals(ownerOf.get(i.projectId())))
                .filter(i -> !"REJECTED".equals(i.status()))
                .map(i -> {
                    Bullet b = new Bullet(i.projectId(), i.text(), i.tags().toArray(new String[0]), i.category());
                    b.setStatus(i.status());
                    b.setStoryId(i.storyId());
                    return b;
                })
                .toList();
        bank = ApplicationService.autoSelectable(bank, ProgressLog.noOp());
        Set<String> covered = new HashSet<>();
        for (Bullet c : ApplicationService.preFilter(bank, kw, lenses)) covered.addAll(KeywordScorer.matched(c, kw));
        return (double) covered.size() / kw.size();
    }

    private static Map<String, Object> side(List<EvalItem> items, Map<UUID, String> sources,
                                            com.resumepipeline.config.GenerationConfig cfg) {
        List<EvalMetrics.Flags> flags = EvalMetrics.flag(items, sources, cfg);
        List<Map<String, Object>> bullets = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("text", items.get(i).text());
            m.put("category", items.get(i).category());
            m.put("status", items.get(i).status());
            m.put("flags", flags.get(i));
            bullets.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", EvalMetrics.summarize(items, sources, cfg));
        out.put("bullets", bullets);
        return out;
    }

    private static Map<String, Object> header(EvalSet s, EvalMetrics.Summary summary) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("label", s.getLabel());
        m.put("note", s.getNote());
        m.put("summary", summary);
        return m;
    }

    /** Every project field generation may quote a number from. */
    static String sourceText(Project p) {
        return Stream.of(p.getDescription(), p.getContextDescription(), p.getRepoContext(), p.getTechStack(),
                        p.getYourRole(), p.getOwnership(), p.getScaleImpact(), p.getHardestProblem(),
                        p.getTechnicalDecisions(), p.getUserImpact(), p.getSecurityPosture(), p.getRepoMap())
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    private EvalSet get(UUID id) {
        return sets.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Eval set not found: " + id));
    }

    private String write(List<EvalItem> items) {
        try {
            return mapper.writeValueAsString(items);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
