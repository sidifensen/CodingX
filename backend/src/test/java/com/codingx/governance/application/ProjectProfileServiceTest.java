package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.governance.application.service.ProjectProfileService;
import com.codingx.governance.domain.model.GovernanceProjectProfile;
import com.codingx.governance.domain.repository.GovernanceProjectProfileRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 验证项目画像扫描只读取仓库结构并生成可复用摘要。
 */
class ProjectProfileServiceTest {

    /**
     * Maven 与 NPM 标记应被识别为技术栈和验证命令。
     * @param tempDir 临时项目目录。
     * @throws Exception 文件创建失败时抛出。
     */
    @Test
    void scanShouldDetectMavenAndNpmProjectMarkers(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("backend/src/main/java/com/example"));
        Files.createDirectories(tempDir.resolve("frontend/user/src"));
        Files.createDirectories(tempDir.resolve("frontend/admin/src/pages"));
        Files.writeString(tempDir.resolve("backend/pom.xml"), "<project></project>", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("frontend/user/package.json"), "{\"scripts\":{\"build\":\"vite build\",\"test:run\":\"vitest run\"}}", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("frontend/admin/package.json"), "{\"scripts\":{\"build\":\"vite build\",\"test:run\":\"vitest run --passWithNoTests\"}}", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("frontend/user/src/main.tsx"), "import React from 'react';", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("frontend/admin/src/main.tsx"), "import React from 'react';", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("frontend/admin/src/pages/GiantPage.tsx"), "x".repeat(70_000), StandardCharsets.UTF_8);
        InMemoryProjectProfileRepository repository = new InMemoryProjectProfileRepository();
        ProjectProfileService service = new ProjectProfileService(repository);

        GovernanceProjectProfile profile = service.scanWorkspace(3001L, tempDir);

        assertTrue(profile.getSummary().contains("Maven"));
        assertTrue(profile.getTechStackJson().contains("Java"));
        assertTrue(profile.getTechStackJson().contains("Node"));
        assertTrue(profile.getVerificationCommandsJson().contains("cd backend && mvn test"));
        assertTrue(profile.getVerificationCommandsJson().contains("npm run build"));
        assertTrue(profile.getModuleMapJson().contains("backend"));
        assertTrue(profile.getModuleMapJson().contains("frontend/user"));
        assertTrue(profile.getModuleMapJson().contains("frontend/admin"));
        assertTrue(profile.getTestCommandsJson().contains("cd frontend/admin && npm run test:run"));
        assertTrue(profile.getKeyEntrypointsJson().contains("frontend/user/src/main.tsx"));
        assertTrue(profile.getRiskPointsJson().contains("GiantPage.tsx"));
        assertTrue(profile.getAgentContext().contains("模块地图"));
        assertTrue(profile.getAgentContext().contains("验证命令"));
        assertTrue(repository.savedProfiles.size() == 1);
    }

    /**
     * 同一个工作空间反复扫描时应刷新当前画像，而不是新增多条主记录污染管理端列表。
     * @param tempDir 临时项目目录。
     * @throws Exception 文件创建失败时抛出。
     */
    @Test
    void scanShouldUpdateCurrentProfileForSameWorkspace(@TempDir Path tempDir) throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>", StandardCharsets.UTF_8);
        InMemoryProjectProfileRepository repository = new InMemoryProjectProfileRepository();
        ProjectProfileService service = new ProjectProfileService(repository);

        GovernanceProjectProfile firstProfile = service.scanWorkspace(3001L, tempDir);
        Files.delete(tempDir.resolve("pom.xml"));
        Files.createDirectories(tempDir.resolve("frontend/user"));
        Files.writeString(tempDir.resolve("frontend/user/package.json"), "{\"scripts\":{\"build\":\"vite build\"}}", StandardCharsets.UTF_8);

        GovernanceProjectProfile secondProfile = service.scanWorkspace(3001L, tempDir);

        assertEquals(firstProfile.getId(), secondProfile.getId());
        assertEquals(firstProfile.getCreatedAt(), secondProfile.getCreatedAt());
        assertNotEquals(firstProfile.getSummary(), secondProfile.getSummary());
        assertTrue(secondProfile.getSummary().contains("NPM"));
        assertEquals(1, repository.savedProfiles.size());
    }

    /**
     * 最近画像列表应按工作空间返回当前画像，历史重复记录不能继续出现在管理端主列表。
     */
    @Test
    void listRecentProfilesShouldReturnOnlyLatestProfilePerWorkspace() {
        InMemoryProjectProfileRepository repository = new InMemoryProjectProfileRepository();
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(5);
        repository.savedProfiles.add(profile(1001L, 3001L, "D:/code/test", "旧画像", baseTime));
        repository.savedProfiles.add(profile(1002L, 3001L, "D:/code/test", "新画像", baseTime.plusMinutes(1)));
        repository.savedProfiles.add(profile(1003L, 3002L, "D:/code/other", "其他画像", baseTime.plusMinutes(2)));
        ProjectProfileService service = new ProjectProfileService(repository);

        List<GovernanceProjectProfile> profiles = service.listRecentProfiles(20);

        assertEquals(2, profiles.size());
        assertEquals("其他画像", profiles.get(0).getSummary());
        assertEquals("新画像", profiles.get(1).getSummary());
    }

    private GovernanceProjectProfile profile(Long id, Long workspaceId, String workspacePath, String summary, LocalDateTime scannedAt) {
        return GovernanceProjectProfile.builder()
            .id(id)
            .workspaceId(workspaceId)
            .workspacePath(workspacePath)
            .summary(summary)
            .techStackJson("[]")
            .entrypointsJson("[]")
            .verificationCommandsJson("[]")
            .moduleMapJson("[]")
            .testCommandsJson("[]")
            .keyEntrypointsJson("[]")
            .riskPointsJson("[]")
            .agentContext("")
            .status("COMPLETED")
            .scannedAt(scannedAt)
            .createdAt(scannedAt)
            .updatedAt(scannedAt)
            .deleted(0)
            .build();
    }

    private static final class InMemoryProjectProfileRepository implements GovernanceProjectProfileRepository {
        private final List<GovernanceProjectProfile> savedProfiles = new ArrayList<>();

        @Override
        public void save(GovernanceProjectProfile profile) {
            for (int index = 0; index < savedProfiles.size(); index++) {
                GovernanceProjectProfile savedProfile = savedProfiles.get(index);
                if (profile.getId() != null && profile.getId().equals(savedProfile.getId())) {
                    savedProfiles.set(index, profile);
                    return;
                }
            }
            savedProfiles.add(profile);
        }

        @Override
        public GovernanceProjectProfile findLatestByWorkspaceId(Long workspaceId) {
            return savedProfiles.stream()
                .filter(profile -> workspaceId.equals(profile.getWorkspaceId()))
                .findFirst()
                .orElse(null);
        }

        @Override
        public List<GovernanceProjectProfile> findRecent(int limit) {
            return savedProfiles;
        }
    }
}
