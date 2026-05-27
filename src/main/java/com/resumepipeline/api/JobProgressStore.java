package com.resumepipeline.api;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for async pipeline job progress.
 * Each job is keyed by a UUID generated at submit time.
 * Single writer per job (the virtual thread running the pipeline),
 * multiple readers (polling GET requests). Snapshots are defensive copies.
 */
@Component
public class JobProgressStore {

    public enum Status { RUNNING, DONE, FAILED }

    public record Snapshot(List<String> lines, Status status, UUID appId, String error) {}

    private record JobState(List<String> lines, Status status, UUID appId, String error, UUID userId, Instant updatedAt) {}

    private final ConcurrentHashMap<UUID, JobState> store = new ConcurrentHashMap<>();

    public void start(UUID jobId, UUID userId) {
        store.put(jobId, new JobState(new ArrayList<>(), Status.RUNNING, null, null, userId, Instant.now()));
    }

    public boolean isOwner(UUID jobId, UUID userId) {
        JobState s = store.get(jobId);
        return s != null && userId.equals(s.userId());
    }

    public void append(UUID jobId, String line) {
        store.compute(jobId, (id, s) -> {
            if (s == null) return null;
            List<String> lines = new ArrayList<>(s.lines());
            lines.add(line);
            return new JobState(lines, s.status(), s.appId(), s.error(), s.userId(), Instant.now());
        });
    }

    public void complete(UUID jobId, UUID appId) {
        store.computeIfPresent(jobId, (id, s) ->
                new JobState(s.lines(), Status.DONE, appId, null, s.userId(), Instant.now()));
    }

    public void fail(UUID jobId, String error) {
        store.computeIfPresent(jobId, (id, s) ->
                new JobState(s.lines(), Status.FAILED, null, error, s.userId(), Instant.now()));
    }

    /** Returns null if jobId unknown. */
    public Snapshot getSnapshot(UUID jobId) {
        JobState s = store.get(jobId);
        if (s == null) return null;
        return new Snapshot(List.copyOf(s.lines()), s.status(), s.appId(), s.error());
    }

    /** Evict jobs older than 10 minutes. Call periodically or on demand. */
    public void cleanup() {
        Instant cutoff = Instant.now().minusSeconds(600);
        store.entrySet().removeIf(e -> e.getValue().updatedAt().isBefore(cutoff));
    }
}
