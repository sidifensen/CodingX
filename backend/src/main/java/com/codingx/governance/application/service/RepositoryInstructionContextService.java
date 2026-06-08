package com.codingx.governance.application.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 仓库规范文件上下文服务，负责只读发现 AGENTS、CLAUDE、GEMINI 以及常见 Agent 工具规则文件。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryInstructionContextService {

    /** 单个规范文件最大注入字符数，避免一个大规则文件撑爆模型上下文。 */
    private static final int MAX_FILE_CHARS = 12_000;
    /** 本轮所有规范文件最大注入字符数，超过后停止追加后续来源。 */
    private static final int MAX_TOTAL_CHARS = 24_000;
    /** 日志预览最大字符数，只打印开头摘要，避免把完整规则写入日志。 */
    private static final int MAX_PREVIEW_CHARS = 48;

    /** 工作空间 Mapper，用于按当前用户和 workspaceId 查询本地工作目录。 */
    private final WorkspaceMapper workspaceMapper;

    /** 精确文件候选，按业界工具约定和本项目优先级稳定排序。 */
    private static final List<InstructionExactCandidate> EXACT_CANDIDATES = List.of(
        new InstructionExactCandidate("Codex/通用", "AGENTS.override.md"),
        new InstructionExactCandidate("Codex/通用", "AGENTS.md"),
        new InstructionExactCandidate("Claude", "CLAUDE.local.md"),
        new InstructionExactCandidate("Claude", "CLAUDE.md"),
        new InstructionExactCandidate("Claude", ".claude/CLAUDE.md"),
        new InstructionExactCandidate("Gemini", "GEMINI.md"),
        new InstructionExactCandidate("Qwen Code", "QWEN.md"),
        new InstructionExactCandidate("Cursor", ".cursorrules"),
        new InstructionExactCandidate("Windsurf", ".windsurfrules"),
        new InstructionExactCandidate("Cline", ".clinerules"),
        new InstructionExactCandidate("Roo", ".roorules"),
        new InstructionExactCandidate("JetBrains Junie", ".junie/guidelines.md"),
        new InstructionExactCandidate("Aider", "CONVENTIONS.md")
    );

    /** 目录候选，扫描时只读取指定后缀，避免全仓库搜索变成索引任务。 */
    private static final List<InstructionDirectoryCandidate> DIRECTORY_CANDIDATES = List.of(
        new InstructionDirectoryCandidate("Claude", ".claude/rules", ".md"),
        new InstructionDirectoryCandidate("Cursor", ".cursor/rules", ".mdc"),
        new InstructionDirectoryCandidate("Windsurf", ".windsurf/rules", ".md"),
        new InstructionDirectoryCandidate("Cline", ".cline/rules", ".md"),
        new InstructionDirectoryCandidate("Roo", ".roo/rules", ".md"),
        new InstructionDirectoryCandidate("Continue", ".continue/rules", ".md"),
        new InstructionDirectoryCandidate("OpenHands", ".openhands/microagents", ".md"),
        new InstructionDirectoryCandidate("Kiro", ".kiro/steering", ".md")
    );

    /**
     * 构建当前 workspace 可用的仓库规范文件上下文。
     * @param userId 当前用户 ID，用于限定只能读取用户拥有的本地 workspace。
     * @param workspaceId 当前会话工作空间 ID。
     * @return 可注入模型的仓库规范上下文，未绑定目录或未命中规范文件时返回空字符串。
     */
    public String buildInstructionContext(Long userId, Long workspaceId) {
        Path workspacePath = resolveWorkspacePath(userId, workspaceId);
        if (workspacePath == null) {
            return "";
        }
        log.info("开始识别仓库规范文件: userId={}, workspaceId={}, path={}", userId, workspaceId, workspacePath);
        List<InstructionFile> instructionFiles = discoverInstructionFiles(workspaceId, workspacePath);
        if (instructionFiles.isEmpty()) {
            log.info("未识别到仓库规范文件: workspaceId={}, path={}", workspaceId, workspacePath);
            return "";
        }
        return renderInstructionContext(workspaceId, instructionFiles);
    }

    /**
     * 解析 workspace 工作目录，要求 workspace 属于当前用户且目录真实存在。
     * @param userId 当前用户 ID。
     * @param workspaceId 当前会话工作空间 ID。
     * @return 本地工作目录，缺失或不可用时返回 null。
     */
    private Path resolveWorkspacePath(Long userId, Long workspaceId) {
        if (userId == null || workspaceId == null) {
            return null;
        }
        WorkspaceDO workspace = workspaceMapper.selectOne(new LambdaQueryWrapper<WorkspaceDO>()
            .eq(WorkspaceDO::getId, workspaceId)
            .eq(WorkspaceDO::getCreatedBy, userId)
            .eq(WorkspaceDO::getDeleted, 0)
            .last("LIMIT 1"));
        if (workspace == null || StrUtil.isBlank(workspace.getWorkingDirectory())) {
            return null;
        }
        Path workspacePath = Path.of(workspace.getWorkingDirectory()).toAbsolutePath().normalize();
        if (!Files.isDirectory(workspacePath)) {
            return null;
        }
        return workspacePath;
    }

    /**
     * 按固定候选顺序发现规范文件，同一路径只保留第一次命中，避免重复注入。
     * @param workspaceId 当前会话工作空间 ID，用于发现阶段异常日志定位具体仓库。
     * @param workspacePath 已校验的本地工作目录。
     * @return 规范文件列表。
     */
    private List<InstructionFile> discoverInstructionFiles(Long workspaceId, Path workspacePath) {
        Map<Path, InstructionFile> filesByPath = new LinkedHashMap<>();
        for (InstructionExactCandidate candidate : EXACT_CANDIDATES) {
            Path path = workspacePath.resolve(candidate.relativePath()).normalize();
            addInstructionFile(workspaceId, workspacePath, filesByPath, path, candidate.sourceType());
        }
        for (InstructionDirectoryCandidate candidate : DIRECTORY_CANDIDATES) {
            collectDirectoryCandidate(workspaceId, workspacePath, filesByPath, candidate);
        }
        collectRooVariantRules(workspaceId, workspacePath, filesByPath);
        return new ArrayList<>(filesByPath.values());
    }

    /**
     * 扫描目录型规则文件，目录不存在或文件不可读时跳过，不影响其它候选。
     * @param workspaceId 当前会话工作空间 ID，用于发现阶段异常日志定位具体仓库。
     * @param workspacePath 工作目录。
     * @param filesByPath 已命中的文件集合。
     * @param candidate 目录候选定义。
     */
    private void collectDirectoryCandidate(
        Long workspaceId,
        Path workspacePath,
        Map<Path, InstructionFile> filesByPath,
        InstructionDirectoryCandidate candidate
    ) {
        Path directory = workspacePath.resolve(candidate.relativeDirectory()).normalize();
        if (!directory.startsWith(workspacePath) || !Files.isDirectory(directory)) {
            return;
        }
        try (var stream = Files.walk(directory, 8)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> hasSuffix(path, candidate.fileSuffix()))
                .sorted(Comparator.comparing(path -> toRelativePath(workspacePath, path)))
                .forEach(path -> addInstructionFile(workspaceId, workspacePath, filesByPath, path, candidate.sourceType()));
        } catch (Exception exception) {
            log.warn("扫描仓库规范目录失败: path={}, message={}", directory, exception.getMessage());
        }
    }

    /**
     * Roo 约定允许 `.roo/rules-*` 多目录规则，这里单独扫描以避免对 `.roo` 做无限制宽搜。
     * @param workspaceId 当前会话工作空间 ID，用于发现阶段异常日志定位具体仓库。
     * @param workspacePath 工作目录。
     * @param filesByPath 已命中的文件集合。
     */
    private void collectRooVariantRules(Long workspaceId, Path workspacePath, Map<Path, InstructionFile> filesByPath) {
        Path rooDirectory = workspacePath.resolve(".roo").normalize();
        if (!rooDirectory.startsWith(workspacePath) || !Files.isDirectory(rooDirectory)) {
            return;
        }
        try (var stream = Files.walk(rooDirectory, 8)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> hasSuffix(path, ".md"))
                .filter(path -> {
                    String relativePath = toRelativePath(workspacePath, path);
                    return relativePath.startsWith(".roo/rules-");
                })
                .sorted(Comparator.comparing(path -> toRelativePath(workspacePath, path)))
                .forEach(path -> addInstructionFile(workspaceId, workspacePath, filesByPath, path, "Roo"));
        } catch (Exception exception) {
            log.warn("扫描 Roo 仓库规范目录失败: path={}, message={}", rooDirectory, exception.getMessage());
        }
    }

    /**
     * 添加单个候选文件；显式排除路径、目录、空文件和越界路径都会被跳过。
     * @param workspaceId 当前会话工作空间 ID，用于文件元信息读取失败时定位仓库。
     * @param workspacePath 工作目录。
     * @param filesByPath 已命中的文件集合。
     * @param path 候选文件路径。
     * @param sourceType 来源类型。
     */
    private void addInstructionFile(
        Long workspaceId,
        Path workspacePath,
        Map<Path, InstructionFile> filesByPath,
        Path path,
        String sourceType
    ) {
        if (!path.startsWith(workspacePath) || !Files.isRegularFile(path) || isExcluded(workspacePath, path)) {
            return;
        }
        try {
            if (Files.size(path) <= 0) {
                return;
            }
        } catch (Exception exception) {
            // 发现阶段只读文件大小，失败时跳过当前文件并保留 workspaceId，便于定位异常仓库。
            log.warn(
                "读取仓库规范文件大小失败: workspaceId={}, path={}, message={}",
                workspaceId,
                toRelativePath(workspacePath, path),
                exception.getMessage()
            );
            return;
        }
        filesByPath.putIfAbsent(path, new InstructionFile(sourceType, path, toRelativePath(workspacePath, path)));
    }

    /**
     * 渲染规范文件上下文；读取失败时记录日志并跳过该文件。
     * @param workspaceId 当前会话工作空间 ID。
     * @param instructionFiles 已发现的规范文件。
     * @return 可注入模型的上下文。
     */
    private String renderInstructionContext(Long workspaceId, List<InstructionFile> instructionFiles) {
        StringBuilder builder = new StringBuilder();
        builder.append("# 仓库规范文件\n");
        builder.append("使用方式：以下内容来自仓库内的 Agent/AI 规范文件。请优先遵守与当前任务相关且更具体的规则，不要逐字复述给用户。\n");
        int totalChars = 0;
        for (InstructionFile instructionFile : instructionFiles) {
            try {
                String content = Files.readString(instructionFile.path(), StandardCharsets.UTF_8);
                long bytes = Files.size(instructionFile.path());
                log.info(
                    "识别到仓库规范文件: workspaceId={}, sourceType={}, path={}, bytes={}, preview={}",
                    workspaceId,
                    instructionFile.sourceType(),
                    instructionFile.relativePath(),
                    bytes,
                    preview(content)
                );
                String normalizedContent = StrUtil.nullToDefault(content, "");
                if (normalizedContent.length() > MAX_FILE_CHARS) {
                    normalizedContent = normalizedContent.substring(0, MAX_FILE_CHARS) + "\n\n[内容已截断，剩余内容未注入]";
                    log.warn(
                        "仓库规范文件内容已截断: workspaceId={}, path={}, maxChars={}",
                        workspaceId,
                        instructionFile.relativePath(),
                        MAX_FILE_CHARS
                    );
                }
                if (totalChars + normalizedContent.length() > MAX_TOTAL_CHARS) {
                    log.warn("仓库规范文件总内容已截断: workspaceId={}, maxChars={}", workspaceId, MAX_TOTAL_CHARS);
                    break;
                }
                builder.append("\n## ")
                    .append(instructionFile.relativePath())
                    .append(" (")
                    .append(instructionFile.sourceType())
                    .append(")\n")
                    .append(normalizedContent.trim())
                    .append('\n');
                totalChars += normalizedContent.length();
            } catch (Exception exception) {
                log.warn(
                    "读取仓库规范文件失败: workspaceId={}, path={}, message={}",
                    workspaceId,
                    instructionFile.relativePath(),
                    exception.getMessage()
                );
            }
        }
        String context = builder.toString().trim();
        return "# 仓库规范文件".equals(context) ? "" : context;
    }

    /**
     * 判断路径是否属于显式排除范围。
     * @param workspacePath 工作目录。
     * @param path 候选文件路径。
     * @return true 表示必须跳过。
     */
    private boolean isExcluded(Path workspacePath, Path path) {
        String relativePath = toRelativePath(workspacePath, path);
        return ".github/copilot-instructions.md".equals(relativePath)
            || relativePath.startsWith("docs/superpowers/memory/")
            || ".codingx/context.md".equals(relativePath)
            || ".codingx/rules.md".equals(relativePath);
    }

    /**
     * 将路径转换成仓库相对路径，并统一使用 `/` 作为日志和上下文中的分隔符。
     * @param workspacePath 工作目录。
     * @param path 文件路径。
     * @return 仓库相对路径。
     */
    private String toRelativePath(Path workspacePath, Path path) {
        return workspacePath.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    /**
     * 判断文件名后缀，大小写不敏感。
     * @param path 文件路径。
     * @param suffix 目标后缀。
     * @return 是否匹配。
     */
    private boolean hasSuffix(Path path, String suffix) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        return StrUtil.endWithIgnoreCase(fileName, suffix);
    }

    /**
     * 生成日志预览，压缩换行和空白并限制长度。
     * @param content 文件正文。
     * @return 短预览文本。
     */
    private String preview(String content) {
        String compact = StrUtil.blankToDefault(content, "")
            .replaceAll("\\s+", " ")
            .trim();
        return StrUtil.maxLength(compact, MAX_PREVIEW_CHARS);
    }

    /**
     * 精确文件候选定义。
     * @param sourceType 规则来源类型。
     * @param relativePath 仓库相对路径。
     */
    private record InstructionExactCandidate(String sourceType, String relativePath) {
    }

    /**
     * 目录型规则候选定义。
     * @param sourceType 规则来源类型。
     * @param relativeDirectory 仓库相对目录。
     * @param fileSuffix 允许读取的文件后缀。
     */
    private record InstructionDirectoryCandidate(String sourceType, String relativeDirectory, String fileSuffix) {
    }

    /**
     * 已发现的规范文件。
     * @param sourceType 来源类型。
     * @param path 绝对路径。
     * @param relativePath 仓库相对路径。
     */
    private record InstructionFile(String sourceType, Path path, String relativePath) {
    }
}
