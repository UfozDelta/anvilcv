package com.resumepipeline.api.dto;

import com.resumepipeline.bullet.Bullet;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class BulletDtos {

    public record CreateBulletRequest(@NotBlank String text, List<String> tags, String category) {}

    public record UpdateBulletRequest(String text, List<String> tags) {}

    public record UpdateStatusRequest(@NotBlank String status) {}

    public record PreviewRequest(List<UUID> bulletIds) {}

    public record BulletResponse(
            UUID id,
            UUID projectId,
            String text,
            List<String> tags,
            String category,
            String status,
            Instant createdAt,
            Instant updatedAt,
            UUID storyId,
            /** Null for a storyless bullet, and on every endpoint but the project's bullet list. */
            String storyTitle,
            /** outcome, decision, scale or failure; null when the wording was not labelled. */
            String angle
    ) {
        public static BulletResponse from(Bullet b) {
            return from(b, null);
        }

        public static BulletResponse from(Bullet b, String storyTitle) {
            return new BulletResponse(b.getId(), b.getProjectId(), b.getText(),
                    b.getTags() == null ? List.of() : List.of(b.getTags()),
                    b.getCategory(), b.getStatus(),
                    b.getCreatedAt(), b.getUpdatedAt(), b.getStoryId(), storyTitle, b.getAngle());
        }
    }
}
