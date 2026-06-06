package com.codingx.governance.application.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.codingx.common.exception.BusinessException;
import com.codingx.governance.domain.model.GovernanceProjectProfile;
import com.codingx.governance.domain.repository.GovernanceProjectProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 项目画像应用服务，负责只读扫描工作空间并生成技术栈、入口和验证命令摘要。
 */
@Service
@RequiredArgsConstructor
public class ProjectProfileService {

    /** 项目画像仓储，用于保存扫描结果并向管理端提供最近画像。 */
    private final GovernanceProjectProfileRepository profileRepository;

    /**
     * 扫描工作空间目录并保存项目画像。
     * @param workspaceId 工作空间 ID。
     * @param workspacePath 工作空间路径。
     * @return 保存后的项目画像。
     */
    public GovernanceProjectProfile scanWorkspace(Long workspaceId, Path workspacePath) {
        // 步骤 1：扫描只接受存在的目录，避免读取工作空间之外的文件。
        if (workspacePath == null || !Files.isDirectory(workspacePath)) {
            throw new BusinessException("GOVERNANCE_WORKSPACE_NOT_FOUND", "工作空间目录不存在，无法生成项目画像");
        }
        Path normalizedWorkspacePath = workspacePath.toAbsolutePath().normalize();
        List<String> techStack = new ArrayList<>();
        List<String> entrypoints = new ArrayList<>();
        List<String> verificationCommands = new ArrayList<>();

        // 步骤 2：根据构建文件和包管理文件识别技术栈，尽量只读取文件名与常见入口。
        if (Files.exists(normalizedWorkspacePath.resolve("pom.xml"))) {
            techStack.add("Java");
            techStack.add("Maven");
            entrypoints.add("pom.xml");
            verificationCommands.add("mvn test");
        }
        detectPackageJson(normalizedWorkspacePath, ".", techStack, entrypoints, verificationCommands);
        detectPackageJson(normalizedWorkspacePath, "frontend/user", techStack, entrypoints, verificationCommands);
        detectPackageJson(normalizedWorkspacePath, "frontend/admin", techStack, entrypoints, verificationCommands);
        if (Files.exists(normalizedWorkspacePath.resolve("vite.config.ts"))
            || Files.exists(normalizedWorkspacePath.resolve("vite.config.js"))) {
            techStack.add("Vite");
        }
        // 步骤 3：去重后生成 JSON 摘要并保存，扫描过程不写工作区文件。
        List<String> normalizedTechStack = distinct(techStack);
        List<String> normalizedEntrypoints = distinct(entrypoints);
        List<String> normalizedVerificationCommands = distinct(verificationCommands);
        LocalDateTime now = LocalDateTime.now();
        GovernanceProjectProfile profile = GovernanceProjectProfile.builder()
            .id(IdUtil.getSnowflakeNextId())
            .workspaceId(workspaceId)
            .workspacePath(normalizedWorkspacePath.toString())
            .summary(buildSummary(normalizedTechStack, normalizedEntrypoints))
            .techStackJson(JSONUtil.toJsonStr(normalizedTechStack))
            .entrypointsJson(JSONUtil.toJsonStr(normalizedEntrypoints))
            .verificationCommandsJson(JSONUtil.toJsonStr(normalizedVerificationCommands))
            .status("COMPLETED")
            .scannedAt(now)
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        profileRepository.save(profile);
        return profile;
    }

    /**
     * 查询最近项目画像。
     * @param limit 最大返回条数。
     * @return 项目画像列表。
     */
    public List<GovernanceProjectProfile> listRecentProfiles(int limit) {
        return profileRepository.findRecent(limit);
    }

    /**
     * 查询指定工作空间最近一次画像。
     * @param workspaceId 工作空间 ID。
     * @return 最近画像，未命中返回 null。
     */
    public GovernanceProjectProfile findLatestByWorkspaceId(Long workspaceId) {
        return profileRepository.findLatestByWorkspaceId(workspaceId);
    }

    private void detectPackageJson(
        Path workspacePath,
        String relativeDir,
        List<String> techStack,
        List<String> entrypoints,
        List<String> verificationCommands
    ) {
        Path packageJsonPath = workspacePath.resolve(relativeDir).resolve("package.json").normalize();
        if (!packageJsonPath.startsWith(workspacePath) || !Files.exists(packageJsonPath)) {
            return;
        }
        techStack.add("Node");
        techStack.add("NPM");
        entrypoints.add(".".equals(relativeDir) ? "package.json" : relativeDir + "/package.json");
        String commandPrefix = ".".equals(relativeDir) ? "" : "cd " + relativeDir + " && ";
        verificationCommands.add(commandPrefix + "npm run build");
        verificationCommands.add(commandPrefix + "npm run test:run");
    }

    private String buildSummary(List<String> techStack, List<String> entrypoints) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("techStack", techStack);
        summary.put("entrypoints", entrypoints);
        return "检测到 " + StrUtil.join("、", techStack) + " 项目线索，入口包含 " + StrUtil.join("、", entrypoints);
    }

    private List<String> distinct(List<String> values) {
        return values.stream().filter(StrUtil::isNotBlank).distinct().toList();
    }
}
