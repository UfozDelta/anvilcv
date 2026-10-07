package com.resumepipeline.eval;

import java.util.List;
import java.util.UUID;

/**
 * One bullet in an {@link EvalSet}. Plain data: never a row of the bullet table. {@code storyId}
 * is null for bullets without a story and for items saved before it was recorded.
 */
public record EvalItem(UUID projectId, String projectName, String projectKind,
                       String category, String status, List<String> tags, String text, UUID storyId) {}
