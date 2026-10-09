package com.resumepipeline.api.dto;

import com.resumepipeline.api.dto.BulletDtos.BulletResponse;
import com.resumepipeline.bullet.Bullet;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BulletResponseTest {

    @Test
    void exposesTheJudgeNoteAndLeavesItNullWhenAbsent() {
        Bullet b = new Bullet(UUID.randomUUID(), "Cut latency by batching settlements.", new String[0], "backend");
        b.setJudgeNote("good: concrete result; bad: none");
        assertEquals("good: concrete result; bad: none", BulletResponse.from(b).judgeNote());

        Bullet unscored = new Bullet(UUID.randomUUID(), "Cut latency by batching settlements.", new String[0], "backend");
        assertNull(BulletResponse.from(unscored).judgeNote());
    }
}
