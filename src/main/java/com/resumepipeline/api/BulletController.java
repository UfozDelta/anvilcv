package com.resumepipeline.api;

import com.resumepipeline.api.dto.BulletDtos.BulletResponse;
import com.resumepipeline.api.dto.BulletDtos.CreateBulletRequest;
import com.resumepipeline.api.dto.BulletDtos.UpdateBulletRequest;
import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.bullet.BulletService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
        return BulletResponse.from(bullets.create(AuthUtils.userId(auth), projectId, req.text(), tags));
    }

    @PutMapping("/bullets/{id}")
    public BulletResponse update(Authentication auth, @PathVariable UUID id,
                                 @RequestBody UpdateBulletRequest req) {
        String[] tags = req.tags() == null ? null : req.tags().toArray(new String[0]);
        return BulletResponse.from(bullets.update(AuthUtils.userId(auth), id, req.text(), tags));
    }

    @DeleteMapping("/bullets/{id}")
    public void delete(Authentication auth, @PathVariable UUID id) {
        bullets.delete(AuthUtils.userId(auth), id);
    }
}
