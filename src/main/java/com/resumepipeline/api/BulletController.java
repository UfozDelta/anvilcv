package com.resumepipeline.api;

import com.resumepipeline.api.dto.BulletDtos.BulletResponse;
import com.resumepipeline.api.dto.BulletDtos.CreateBulletRequest;
import com.resumepipeline.api.dto.BulletDtos.PreviewRequest;
import com.resumepipeline.api.dto.BulletDtos.UpdateBulletRequest;
import com.resumepipeline.api.dto.BulletDtos.UpdateStatusRequest;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.bullet.BulletService;
import com.resumepipeline.render.PdfCompiler;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class BulletController {

    private final BulletService bullets;

    public BulletController(BulletService bullets) {
        this.bullets = bullets;
    }

    @GetMapping("/projects/{projectId}/bullets")
    public List<BulletResponse> listForProject(Authentication auth, @PathVariable UUID projectId) {
        return bullets.listForProject(AuthUtils.userId(auth), projectId).stream().map(BulletResponse::from).toList();
    }

    @PostMapping("/projects/{projectId}/bullets")
    public BulletResponse create(Authentication auth, @PathVariable UUID projectId,
                                 @RequestBody @Valid CreateBulletRequest req) {
        String[] tags = req.tags() == null ? new String[0] : req.tags().toArray(new String[0]);
        return BulletResponse.from(bullets.create(AuthUtils.userId(auth), projectId, req.text(), tags, req.category()));
    }

    @PutMapping("/bullets/{id}")
    public BulletResponse update(Authentication auth, @PathVariable UUID id,
                                 @RequestBody UpdateBulletRequest req) {
        String[] tags = req.tags() == null ? null : req.tags().toArray(new String[0]);
        return BulletResponse.from(bullets.update(AuthUtils.userId(auth), id, req.text(), tags));
    }

    @PatchMapping("/bullets/{id}/status")
    public BulletResponse updateStatus(Authentication auth, @PathVariable UUID id,
                                       @RequestBody @Valid UpdateStatusRequest req) {
        return BulletResponse.from(bullets.updateStatus(AuthUtils.userId(auth), id, req.status()));
    }

    /**
     * Renders these bullets alone as a PDF - no header, education or skills - so a
     * selection can be checked without touching a saved application. Nothing is stored.
     *
     * <p>Synchronous: tectonic takes a few seconds and {@code PdfCompiler} bounds how
     * many run at once, so there is no job to poll.
     */
    @PostMapping("/bullets/preview")
    public ResponseEntity<byte[]> preview(Authentication auth, @RequestBody PreviewRequest req) {
        PdfCompiler.Result r = bullets.preview(AuthUtils.userId(auth), req.bulletIds());
        HttpHeaders h = new HttpHeaders();
        if (!r.success()) {
            // Content-Type is set per branch: the failure body is the tectonic log, not a PDF.
            h.setContentType(MediaType.TEXT_PLAIN);
            String body = r.error() + System.lineSeparator() + r.log();
            return new ResponseEntity<>(body.getBytes(StandardCharsets.UTF_8), h, HttpStatus.UNPROCESSABLE_ENTITY);
        }
        h.setContentType(MediaType.APPLICATION_PDF);
        h.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"bullet-preview.pdf\"");
        return new ResponseEntity<>(r.pdf(), h, HttpStatus.OK);
    }

    @DeleteMapping("/bullets/{id}")
    public void delete(Authentication auth, @PathVariable UUID id) {
        bullets.delete(AuthUtils.userId(auth), id);
    }
}
