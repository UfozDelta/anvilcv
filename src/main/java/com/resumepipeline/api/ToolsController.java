package com.resumepipeline.api;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
public class ToolsController {

    @GetMapping("/context-agent")
    public ResponseEntity<Resource> contextAgent() {
        Resource resource = new ClassPathResource("anvilcv-context-agent.md");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/markdown"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"anvilcv-context-agent.md\"")
                .body(resource);
    }

    /**
     * Plain-chat variant of the context agent, for the "COPY FOR LLM" flow: no YAML
     * frontmatter (meaningless outside an agent-file loader) and Stage 5.5 rewritten to
     * ask in chat rather than call a tool that only exists in Claude Code. Served inline,
     * not as an attachment, so the frontend reads the body directly for the clipboard.
     */
    @GetMapping("/context-agent/prompt")
    public ResponseEntity<Resource> contextAgentPrompt() {
        Resource resource = new ClassPathResource("anvilcv-context-copy-prompt.md");
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(resource);
    }
}
