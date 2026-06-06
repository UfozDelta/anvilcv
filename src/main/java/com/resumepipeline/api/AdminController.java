package com.resumepipeline.api;

import com.resumepipeline.application.Application;
import com.resumepipeline.application.ApplicationRepository;
import com.resumepipeline.llm.LlmUsageLog;
import com.resumepipeline.llm.LlmUsageLogRepository;
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
    private final LlmUsageLogRepository usageRepo;

    public AdminController(ApplicationRepository repo, LlmUsageLogRepository usageRepo) {
        this.repo = repo;
        this.usageRepo = usageRepo;
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

        // LLM usage log aggregates (covers bullet generation + application pipeline)
        List<LlmUsageLog> usageLogs = usageRepo.findAll();
        Map<String, BigDecimal> costBySource = usageLogs.stream()
                .collect(Collectors.groupingBy(LlmUsageLog::getSource,
                        Collectors.reducing(BigDecimal.ZERO, LlmUsageLog::getCostUsd, BigDecimal::add)));
        Map<String, Integer> promptBySource = usageLogs.stream()
                .collect(Collectors.groupingBy(LlmUsageLog::getSource,
                        Collectors.summingInt(LlmUsageLog::getPromptTokens)));
        Map<String, Integer> candidatesBySource = usageLogs.stream()
                .collect(Collectors.groupingBy(LlmUsageLog::getSource,
                        Collectors.summingInt(LlmUsageLog::getCandidatesTokens)));

        List<Map<String, Object>> usageBySource = costBySource.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("source", e.getKey());
                    m.put("promptTokens", promptBySource.getOrDefault(e.getKey(), 0));
                    m.put("candidatesTokens", candidatesBySource.getOrDefault(e.getKey(), 0));
                    m.put("costUsd", e.getValue().setScale(8, RoundingMode.HALF_UP));
                    return m;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("costUsd")).compareTo((BigDecimal) a.get("costUsd")))
                .toList();

        BigDecimal totalLogCost = usageLogs.stream()
                .map(LlmUsageLog::getCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(8, RoundingMode.HALF_UP);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalApplications", all.size());
        result.put("totalPromptTokens", totalPrompt);
        result.put("totalCandidatesTokens", totalCandidates);
        result.put("totalCostUsd", totalCost);
        result.put("perUser", perUser);
        result.put("perApplication", perApp);
        result.put("usageLogTotalCostUsd", totalLogCost);
        result.put("usageLogBySource", usageBySource);
        return result;
    }
}
