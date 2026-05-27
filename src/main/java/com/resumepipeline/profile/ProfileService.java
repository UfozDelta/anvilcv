package com.resumepipeline.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {

    private final ProfileRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProfileService(ProfileRepository repo) {
        this.repo = repo;
    }

    public Profile get(UUID userId) {
        return repo.findByUserId(userId).orElseGet(() -> {
            Profile p = new Profile();
            p.setId(UUID.randomUUID());
            p.setUserId(userId);
            p.setUpdatedAt(Instant.now());
            return repo.save(p);
        });
    }

    public Profile update(UUID userId, ProfileDto dto) {
        Profile p = get(userId);
        p.setName(dto.name());
        p.setPhone(dto.phone());
        p.setEmail(dto.email());
        p.setLinkedinHandle(dto.linkedinHandle());
        p.setGithubHandle(dto.githubHandle());
        p.setPortfolioUrl(dto.portfolioUrl());
        try {
            p.setEducation(mapper.writeValueAsString(dto.education() == null ? List.of() : dto.education()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize education", e);
        }
        p.setSkillsLanguages(dto.skillsLanguages());
        p.setSkillsFrameworks(dto.skillsFrameworks());
        p.setSkillsDatabases(dto.skillsDatabases());
        p.setSkillsDevops(dto.skillsDevops());
        p.setSkillsInterests(dto.skillsInterests());
        p.setUpdatedAt(Instant.now());
        return repo.save(p);
    }

    public List<EducationEntry> readEducation(Profile p) {
        try {
            return mapper.readValue(p.getEducation(), new TypeReference<List<EducationEntry>>() {});
        } catch (Exception e) { return List.of(); }
    }

    public record EducationEntry(String school, String location, String degree, String dates, String coursework) {}

    public record ProfileDto(
            String name, String phone, String email,
            String linkedinHandle, String githubHandle, String portfolioUrl,
            List<EducationEntry> education,
            String skillsLanguages, String skillsFrameworks, String skillsDatabases,
            String skillsDevops, String skillsInterests
    ) {
        public static ProfileDto from(Profile p, ProfileService svc) {
            return new ProfileDto(
                    p.getName(), p.getPhone(), p.getEmail(),
                    p.getLinkedinHandle(), p.getGithubHandle(), p.getPortfolioUrl(),
                    svc.readEducation(p),
                    p.getSkillsLanguages(), p.getSkillsFrameworks(), p.getSkillsDatabases(),
                    p.getSkillsDevops(), p.getSkillsInterests()
            );
        }
    }
}
