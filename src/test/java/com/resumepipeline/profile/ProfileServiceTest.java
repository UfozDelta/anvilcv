package com.resumepipeline.profile;

import com.resumepipeline.application.ApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock ProfileRepository repo;
    @Mock ApplicationRepository applicationRepo;
    @InjectMocks ProfileService service;

    @Test
    void getLazyCreatesWhenMissing() {
        UUID user = UUID.randomUUID();
        when(repo.findByUserId(user)).thenReturn(Optional.empty());
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Profile p = service.get(user);

        assertEquals(user, p.getUserId());
        assertNotNull(p.getId());
        verify(repo).save(p);
    }

    @Test
    void updateSerializesEducationToJson() {
        UUID user = UUID.randomUUID();
        Profile existing = new Profile();
        existing.setUserId(user);
        when(repo.findByUserId(user)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ProfileService.EducationEntry edu = new ProfileService.EducationEntry(
                "MIT", "Cambridge", "BSc CS", "2020-2024", "Algorithms, OS");
        ProfileService.ProfileDto dto = new ProfileService.ProfileDto(
                "Ada", "555", "ada@x.com", "ada-li", "ada-gh", "ada.dev",
                List.of(edu), "Java", "Spring", "Postgres", "Docker", "chess");

        Profile saved = service.update(user, dto);

        assertEquals("Ada", saved.getName());
        assertEquals("Java", saved.getSkillsLanguages());
        // Education was serialized; reading it back yields the same entry.
        List<ProfileService.EducationEntry> readBack = service.readEducation(saved);
        assertEquals(List.of(edu), readBack);
    }

    @Test
    void changingAPrintedFieldFlagsEveryPdfButASameValueSaveDoesNot() {
        UUID user = UUID.randomUUID();
        Profile existing = new Profile();
        existing.setUserId(user);
        when(repo.findByUserId(user)).thenReturn(Optional.of(existing));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ProfileService.ProfileDto dto = new ProfileService.ProfileDto(
                "Ada", "555", "ada@x.com", "ada-li", "ada-gh", "ada.dev",
                List.of(), "Java", "Spring", "Postgres", "Docker", "chess");

        service.update(user, dto);
        verify(applicationRepo).markPdfStaleForUser(user);

        // The form posts every field on each save; an unchanged one must not date the PDFs.
        clearInvocations(applicationRepo);
        service.update(user, dto);
        verifyNoInteractions(applicationRepo);
    }

    @Test
    void readEducationReturnsEmptyOnBadJson() {
        Profile p = new Profile();
        p.setEducation("{not valid json");
        assertEquals(List.of(), service.readEducation(p));
    }

    @Test
    void readEducationReturnsEmptyOnNull() {
        Profile p = new Profile();
        p.setEducation(null);
        assertEquals(List.of(), service.readEducation(p));
    }
}
