package com.resumepipeline.api;

import com.resumepipeline.auth.AuthUtils;
import com.resumepipeline.field.Field;
import com.resumepipeline.field.FieldPreset;
import com.resumepipeline.field.FieldPresets;
import com.resumepipeline.profile.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * The user's field and what it changes. Separate from /api/profile on purpose: that PUT replaces
 * the whole form, so a client that does not know about the field would reset it.
 */
@RestController
@RequestMapping("/api/field")
public class FieldController {

    private final ProfileService profiles;

    public FieldController(ProfileService profiles) {
        this.profiles = profiles;
    }

    public record FieldView(String field, FieldPreset preset, List<FieldPreset> available) {}
    public record FieldChange(String field) {}

    @GetMapping
    public FieldView get(Authentication auth) {
        return view(profiles.field(AuthUtils.userId(auth)));
    }

    @PutMapping
    public FieldView set(Authentication auth, @RequestBody FieldChange body) {
        if (body == null || !Field.isKnown(body.field())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown field: " + (body == null ? null : body.field()));
        }
        return view(profiles.setField(AuthUtils.userId(auth), Field.of(body.field())).getField());
    }

    private static FieldView view(Field f) {
        return new FieldView(f.slug(), FieldPresets.of(f), FieldPresets.all());
    }
}
