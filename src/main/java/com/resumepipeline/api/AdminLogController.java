package com.resumepipeline.api;

import com.resumepipeline.obs.RingBufferLogAppender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only read path over the in-process ring buffer ({@link RingBufferLogAppender}) — see
 * logging-plan.md Layer 6. Gated on {@code ROLE_ADMIN} the same way as {@link AdminLlmController},
 * via SecurityConfig's {@code /api/admin/**} rule; nothing here re-checks the role.
 */
@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {

    private final RingBufferLogAppender appender;

    public AdminLogController(RingBufferLogAppender appender) {
        this.appender = appender;
    }

    @GetMapping
    public List<String> get(@RequestParam(required = false) String contains,
                            @RequestParam(required = false) String level) {
        return appender.snapshot(contains, level);
    }
}
