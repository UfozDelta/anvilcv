package com.resumepipeline.profile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumepipeline.application.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository repo;
    // The header, education and skills print on every page, so a change there dates every PDF.
    private final ApplicationRepository applicationRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProfileService(ProfileRepository repo, ApplicationRepository applicationRepo) {
        this.repo = repo;
        this.applicationRepo = applicationRepo;
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
        List<Object> printedBefore = printed(p);
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
        Profile saved = repo.save(p);
        if (!printed(saved).equals(printedBefore)) {
            // Never fails the save. Skills can over-flag: a page with its own selected skills for
            // a category prints those, not the profile's.
            try {
                applicationRepo.markPdfStaleForUser(userId);
            } catch (RuntimeException e) {
                log.warn("Could not flag application PDFs stale after profile edit: {}", e.getMessage());
            }
        }
        return saved;
    }

    /** Every field ApplicationRenderer reads from the profile. */
    private static List<Object> printed(Profile p) {
        return Arrays.asList(p.getName(), p.getPhone(), p.getEmail(), p.getLinkedinHandle(), p.getGithubHandle(),
                p.getPortfolioUrl(), p.getEducation(), p.getSkillsLanguages(), p.getSkillsFrameworks(),
                p.getSkillsDatabases(), p.getSkillsDevops(), p.getSkillsInterests());
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
