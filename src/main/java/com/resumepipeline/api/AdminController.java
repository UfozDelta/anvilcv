package com.resumepipeline.api;

import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ApplicationRepository repo;

    public AdminController(ApplicationRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<Application> all = repo.findAllOrderByCreatedAtDesc();

        int totalPrompt     = all.stream().mapToInt(Application::getLlmPromptTokens).sum();
        int totalCandidates = all.stream().mapToInt(Application::getLlmCandidatesTokens).sum();
        BigDecimal totalCost = all.stream()
                .map(Application::getLlmCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);

        Map<UUID, List<Application>> byUser = all.stream()
                .collect(Collectors.groupingBy(Application::getUserId));

        List<Map<String, Object>> perUser = byUser.entrySet().stream()
                .map(e -> {
                    List<Application> apps = e.getValue();
                    BigDecimal userCost = apps.stream()
                            .map(Application::getLlmCostUsd)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .setScale(8, RoundingMode.HALF_UP);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userId", e.getKey());
                    m.put("applicationCount", apps.size());
                    m.put("promptTokens", apps.stream().mapToInt(Application::getLlmPromptTokens).sum());
                    m.put("candidatesTokens", apps.stream().mapToInt(Application::getLlmCandidatesTokens).sum());
                    m.put("costUsd", userCost);
                    return m;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("costUsd")).compareTo((BigDecimal) a.get("costUsd")))
                .toList();

        List<Map<String, Object>> perApp = all.stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("userId", a.getUserId());
                    m.put("company", a.getCompany());
                    m.put("role", a.getRole());
                    m.put("createdAt", a.getCreatedAt());
                    m.put("promptTokens", a.getLlmPromptTokens());
                    m.put("candidatesTokens", a.getLlmCandidatesTokens());
                    m.put("costUsd", a.getLlmCostUsd());
                    return m;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalApplications", all.size());
        result.put("totalPromptTokens", totalPrompt);
        result.put("totalCandidatesTokens", totalCandidates);
        result.put("totalCostUsd", totalCost);
        result.put("perUser", perUser);
        result.put("perApplication", perApp);
        return result;
    }
}
