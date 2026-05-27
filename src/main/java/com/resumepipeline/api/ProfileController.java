package com.resumepipeline.api;

import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.profile.ProfileService;
import com.resumepipeline.profile.ProfileService.ProfileDto;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ProfileDto get(Authentication auth) {
        return ProfileDto.from(service.get(AuthUtils.userId(auth)), service);
    }

    @PutMapping
    public ProfileDto update(Authentication auth, @RequestBody ProfileDto dto) {
        return ProfileDto.from(service.update(AuthUtils.userId(auth), dto), service);
    }
}
