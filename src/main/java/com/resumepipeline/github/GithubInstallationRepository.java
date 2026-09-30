package com.resumepipeline.github;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GithubInstallationRepository extends JpaRepository<GithubInstallation, UUID> {
}
