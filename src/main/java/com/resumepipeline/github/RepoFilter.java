package com.resumepipeline.github;

import java.util.Locale;
import java.util.Set;

/**
 * Paths the explorer and the tree view skip: vendored/generated directories, lockfiles, and
 * binaries. None of it says anything about what the author built, and all of it burns the
 * explorer's read budget.
 */
public final class RepoFilter {

    private RepoFilter() {}

    private static final Set<String> NOISE_DIRS = Set.of(
            "node_modules", "vendor", "third_party", "dist", "build", "target", "out", ".git",
            ".next", ".nuxt", "coverage", "__pycache__", ".venv", "venv", ".gradle", ".idea", ".vscode");

    private static final Set<String> LOCKFILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "go.sum", "cargo.lock", "poetry.lock",
            "pipfile.lock", "composer.lock", "gemfile.lock", "bun.lockb", "uv.lock");

    private static final Set<String> BINARY_EXT = Set.of(
            "png", "jpg", "jpeg", "gif", "ico", "webp", "bmp", "svg", "pdf", "zip", "gz", "tgz", "tar",
            "jar", "war", "class", "exe", "dll", "so", "dylib", "bin", "o", "a", "woff", "woff2",
            "ttf", "otf", "eot", "mp3", "mp4", "mov", "wav", "avi", "psd", "sqlite", "db", "pyc", "map");

    public static boolean isNoise(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String[] parts = lower.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (NOISE_DIRS.contains(parts[i])) return true;
        }
        String name = parts[parts.length - 1];
        if (LOCKFILES.contains(name) || name.endsWith(".min.js") || name.endsWith(".min.css")) return true;
        int dot = name.lastIndexOf('.');
        return dot >= 0 && BINARY_EXT.contains(name.substring(dot + 1));
    }
}
