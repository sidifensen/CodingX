package com.codingx.governance.application;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.governance.application.service.ProjectProfileService;
import com.codingx.governance.domain.model.GovernanceProjectProfile;
import com.codingx.governance.domain.repository.GovernanceProjectProfileRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static final class InMemoryProjectProfileRepository implements GovernanceProjectProfileRepository {
        private final List<GovernanceProjectProfile> savedProfiles = new ArrayList<>();

        @Override
        public void save(GovernanceProjectProfile profile) {
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
