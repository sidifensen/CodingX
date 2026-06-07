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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
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
        List<Map<String, Object>> moduleMap = new ArrayList<>();
        List<String> testCommands = new ArrayList<>();
        List<String> keyEntrypoints = new ArrayList<>();
        List<String> riskPoints = new ArrayList<>();

        // 步骤 2：根据构建文件和包管理文件识别技术栈，尽量只读取文件名与常见入口。
        if (Files.exists(normalizedWorkspacePath.resolve("pom.xml"))) {
            techStack.add("Java");
            techStack.add("Maven");
            entrypoints.add("pom.xml");
            verificationCommands.add("mvn test");
            testCommands.add("mvn test");
            moduleMap.add(module("root", ".", "Java/Maven", List.of("pom.xml")));
        }
        detectMavenModule(normalizedWorkspacePath, "backend", techStack, entrypoints, verificationCommands, testCommands, moduleMap, keyEntrypoints);
        detectPackageJson(normalizedWorkspacePath, ".", techStack, entrypoints, verificationCommands, testCommands, moduleMap, keyEntrypoints);
        detectPackageJson(normalizedWorkspacePath, "frontend/user", techStack, entrypoints, verificationCommands, testCommands, moduleMap, keyEntrypoints);
        detectPackageJson(normalizedWorkspacePath, "frontend/admin", techStack, entrypoints, verificationCommands, testCommands, moduleMap, keyEntrypoints);
        if (Files.exists(normalizedWorkspacePath.resolve("vite.config.ts"))
            || Files.exists(normalizedWorkspacePath.resolve("vite.config.js"))) {
            techStack.add("Vite");
        }
        detectEntrypoint(normalizedWorkspacePath, "frontend/user/src/main.tsx", keyEntrypoints);
        detectEntrypoint(normalizedWorkspacePath, "frontend/user/src/App.tsx", keyEntrypoints);
        detectEntrypoint(normalizedWorkspacePath, "frontend/admin/src/main.tsx", keyEntrypoints);
        detectEntrypoint(normalizedWorkspacePath, "frontend/admin/src/App.tsx", keyEntrypoints);
        detectLargeFiles(normalizedWorkspacePath, riskPoints);
        // 步骤 3：去重后生成 JSON 摘要并保存，扫描过程不写工作区文件。
        List<String> normalizedTechStack = distinct(techStack);
        List<String> normalizedEntrypoints = distinct(entrypoints);
        List<String> normalizedVerificationCommands = distinct(verificationCommands);
        List<String> normalizedTestCommands = distinct(testCommands);
        List<String> normalizedKeyEntrypoints = distinct(keyEntrypoints);
        List<String> normalizedRiskPoints = distinct(riskPoints);
        LocalDateTime now = LocalDateTime.now();
        GovernanceProjectProfile profile = GovernanceProjectProfile.builder()
            .id(IdUtil.getSnowflakeNextId())
            .workspaceId(workspaceId)
            .workspacePath(normalizedWorkspacePath.toString())
            .summary(buildSummary(normalizedTechStack, normalizedEntrypoints))
            .techStackJson(JSONUtil.toJsonStr(normalizedTechStack))
            .entrypointsJson(JSONUtil.toJsonStr(normalizedEntrypoints))
            .verificationCommandsJson(JSONUtil.toJsonStr(normalizedVerificationCommands))
            .moduleMapJson(JSONUtil.toJsonStr(moduleMap))
            .testCommandsJson(JSONUtil.toJsonStr(normalizedTestCommands))
            .keyEntrypointsJson(JSONUtil.toJsonStr(normalizedKeyEntrypoints))
            .riskPointsJson(JSONUtil.toJsonStr(normalizedRiskPoints))
            .agentContext(buildAgentContext(moduleMap, normalizedTestCommands, normalizedKeyEntrypoints, normalizedRiskPoints))
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
        List<String> verificationCommands,
        List<String> testCommands,
        List<Map<String, Object>> moduleMap,
        List<String> keyEntrypoints
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
        testCommands.add(commandPrefix + "npm run test:run");
        moduleMap.add(module(moduleCode(relativeDir), relativeDir, "React/Vite/NPM", List.of(packageJsonPath.getFileName().toString())));
        detectEntrypoint(workspacePath, relativeDir + "/src/main.tsx", keyEntrypoints);
        detectEntrypoint(workspacePath, relativeDir + "/src/App.tsx", keyEntrypoints);
    }

    private void detectMavenModule(
        Path workspacePath,
        String relativeDir,
        List<String> techStack,
        List<String> entrypoints,
        List<String> verificationCommands,
        List<String> testCommands,
        List<Map<String, Object>> moduleMap,
        List<String> keyEntrypoints
    ) {
        Path pomPath = workspacePath.resolve(relativeDir).resolve("pom.xml").normalize();
        if (!pomPath.startsWith(workspacePath) || !Files.exists(pomPath)) {
            return;
        }
        techStack.add("Java");
        techStack.add("Maven");
        entrypoints.add(relativeDir + "/pom.xml");
        verificationCommands.add("cd " + relativeDir + " && mvn test");
        testCommands.add("cd " + relativeDir + " && mvn test");
        moduleMap.add(module(moduleCode(relativeDir), relativeDir, "Spring Boot/Maven", List.of("pom.xml", "src/main/java")));
        detectEntrypoint(workspacePath, relativeDir + "/src/main/resources/application.yml", keyEntrypoints);
        detectEntrypoint(workspacePath, relativeDir + "/src/main/resources/application.yaml", keyEntrypoints);
        detectEntrypoint(workspacePath, relativeDir + "/src/main/resources/application.properties", keyEntrypoints);
    }

    private void detectEntrypoint(Path workspacePath, String relativePath, List<String> keyEntrypoints) {
        Path entrypointPath = workspacePath.resolve(relativePath).normalize();
        if (entrypointPath.startsWith(workspacePath) && Files.exists(entrypointPath)) {
            keyEntrypoints.add(relativePath.replace('\\', '/'));
        }
    }

    private void detectLargeFiles(Path workspacePath, List<String> riskPoints) {
        // 步骤 1：只扫描常见源码目录并限制风险提示数量，避免画像扫描变成全量索引任务。
        List<String> candidateRoots = List.of("backend/src/main/java", "frontend/user/src", "frontend/admin/src");
        for (String candidateRoot : candidateRoots) {
            Path root = workspacePath.resolve(candidateRoot).normalize();
            if (!root.startsWith(workspacePath) || !Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> pathStream = Files.walk(root, 12)) {
                pathStream
                    .filter(Files::isRegularFile)
                    .filter(this::isCodeFile)
                    .filter(path -> fileSize(path) >= 60_000)
                    .limit(5)
                    .forEach(path -> riskPoints.add("大文件风险：" + relativePath(workspacePath, path) + " 文件较大，建议重点拆分或补测试"));
            } catch (Exception ignored) {
                // 扫描风险点失败不影响项目画像主结果，避免单个不可读文件阻断绑定流程。
            }
        }
    }

    private boolean isCodeFile(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        return StrUtil.endWithAnyIgnoreCase(fileName, ".java", ".tsx", ".ts", ".jsx", ".js", ".vue");
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception exception) {
            return 0L;
        }
    }

    private String relativePath(Path workspacePath, Path path) {
        return workspacePath.relativize(path).toString().replace('\\', '/');
    }

    private Map<String, Object> module(String moduleCode, String modulePath, String stack, List<String> markers) {
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("moduleCode", moduleCode);
        module.put("modulePath", modulePath);
        module.put("stack", stack);
        module.put("markers", markers);
        return module;
    }

    private String moduleCode(String relativeDir) {
        if (StrUtil.isBlank(relativeDir) || ".".equals(relativeDir)) {
            return "root";
        }
        return relativeDir.replace('\\', '/');
    }

    private String buildSummary(List<String> techStack, List<String> entrypoints) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("techStack", techStack);
        summary.put("entrypoints", entrypoints);
        return "检测到 " + StrUtil.join("、", techStack) + " 项目线索，入口包含 " + StrUtil.join("、", entrypoints);
    }

    private String buildAgentContext(
        List<Map<String, Object>> moduleMap,
        List<String> testCommands,
        List<String> keyEntrypoints,
        List<String> riskPoints
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 项目画像\n");
        builder.append("模块地图：").append(JSONUtil.toJsonStr(moduleMap)).append('\n');
        builder.append("验证命令：").append(StrUtil.join("；", testCommands)).append('\n');
        if (!keyEntrypoints.isEmpty()) {
            builder.append("关键入口：").append(StrUtil.join("；", keyEntrypoints)).append('\n');
        }
        if (!riskPoints.isEmpty()) {
            builder.append("风险点：").append(StrUtil.join("；", riskPoints)).append('\n');
        }
        builder.append("使用约束：优先按模块边界阅读代码，修改后按受影响模块执行验证命令。");
        return builder.toString();
    }

    private List<String> distinct(List<String> values) {
        Set<String> distinctValues = new LinkedHashSet<>();
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                distinctValues.add(value);
            }
        }
        return distinctValues.stream().toList();
    }
}
