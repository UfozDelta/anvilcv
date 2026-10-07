package com.resumepipeline.api;

import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.eval.EvalService;
import com.resumepipeline.eval.EvalSet;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin-only bullet eval. The whole {@code /api/admin/**} tree is gated on ROLE_ADMIN by
 * SecurityConfig; nothing here re-checks the role.
 */
@RestController
@RequestMapping("/api/admin/eval")
public class AdminEvalController {

    private final EvalService eval;

    public AdminEvalController(EvalService eval) {
        this.eval = eval;
    }

    public record GenerateRequest(UUID referenceSetId, List<UUID> projectIds, String note) {}

    @GetMapping("/sets")
    public List<Map<String, Object>> sets() {
        return eval.list().stream().map(this::view).toList();
    }

    /** Snapshots the calling admin's own bullet bank (not every user's). */
    @PostMapping("/snapshot")
    public Map<String, Object> snapshot(Authentication auth) {
        return view(eval.snapshotBank(AuthUtils.userId(auth)));
    }

    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody GenerateRequest req) {
        return view(eval.startGeneration(req.referenceSetId(), req.projectIds(), req.note()));
    }

    @DeleteMapping("/sets/{id}")
    public void delete(@PathVariable UUID id) {
        eval.delete(id);
    }

    @GetMapping("/compare")
    public Map<String, Object> compare(@RequestParam UUID a, @RequestParam UUID b) {
        return eval.compare(a, b);
    }

    private Map<String, Object> view(EvalSet s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("label", s.getLabel());
        m.put("source", s.getSource());
        m.put("note", s.getNote());
        m.put("status", s.getStatus());
        m.put("error", s.getError());
        m.put("createdAt", s.getCreatedAt());
        var items = eval.items(s);
        m.put("count", items.size());
        // Project picker for "generate": the projects this set covers.
        Map<UUID, String> projects = new LinkedHashMap<>();
        items.forEach(i -> projects.putIfAbsent(i.projectId(), i.projectName()));
        m.put("projects", projects.entrySet().stream()
                .map(e -> Map.of("id", e.getKey(), "name", e.getValue())).toList());
        return m;
    }
}
