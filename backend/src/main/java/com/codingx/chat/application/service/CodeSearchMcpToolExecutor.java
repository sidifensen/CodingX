package com.codingx.chat.application.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.config.RuntimeProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 代码检索 MCP 工具执行器。
 * <p>
 * 该执行器用于在本地代码目录内执行关键词检索，并返回可读的命中路径、行号与片段。
 */
@Component
public class CodeSearchMcpToolExecutor implements ChatMcpToolExecutor {

    /**
     * 工具唯一标识。
     */
    private static final String TOOL_ID = "code_search";

    /**
     * 需要排除的目录，避免扫描依赖产物和大型目录。
     */
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
        ".git", "node_modules", "target", "dist", "build", "logs", ".idea", ".vscode"
    );

    /**
     * 允许扫描的代码文件扩展名。
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "java", "kt", "xml", "yml", "yaml", "properties", "sql",
        "js", "ts", "tsx", "jsx", "css", "scss", "md"
    );

    private final Path searchRoot;
    private final int maxResults;
    private final long maxFileSizeBytes;

    /**
     * 运行时构造，支持从配置读取检索根目录与限制项。
     *
     * @param runtimeProperties 运行时配置。
     */
    @Autowired
    public CodeSearchMcpToolExecutor(RuntimeProperties runtimeProperties) {
        this(resolveSearchRoot(runtimeProperties == null ? null : runtimeProperties.getCodeSearchRoot()),
            normalizeMaxResults(runtimeProperties == null ? 20 : runtimeProperties.getCodeSearchMaxResults()),
            normalizeMaxFileSize(runtimeProperties == null ? 1024 * 1024L : runtimeProperties.getCodeSearchMaxFileSizeBytes()));
    }

    /**
     * 测试专用构造，便于隔离临时目录。
     *
     * @param rootPath 检索根目录。
     * @param maxResults 最大返回条数。
     * @param maxFileSizeBytes 单文件最大扫描字节数。
     */
    CodeSearchMcpToolExecutor(String rootPath, int maxResults, long maxFileSizeBytes) {
        this(resolveSearchRoot(rootPath), normalizeMaxResults(maxResults), normalizeMaxFileSize(maxFileSizeBytes));
    }

    private CodeSearchMcpToolExecutor(Path searchRoot, int maxResults, long maxFileSizeBytes) {
        this.searchRoot = searchRoot;
        this.maxResults = maxResults;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Override
    public String toolId() {
        return TOOL_ID;
    }

    /**
     * 执行代码检索，并返回路径、行号与摘要。
     *
     * @param question 用户问题。
     * @return 检索结果。
     */
    @Override
    public ChatMcpToolResult execute(String question) {
        String safeQuestion = StrUtil.blankToDefault(question, "");
        List<String> terms = parseSearchTerms(safeQuestion);
        if (terms.isEmpty()) {
            return new ChatMcpToolResult(toolId(), "请提供要检索的代码关键词，例如：查找 sendMessage 实现", Map.of("error", true));
        }

        if (!Files.exists(searchRoot) || !Files.isDirectory(searchRoot)) {
            return new ChatMcpToolResult(toolId(), "代码目录不存在，无法执行检索", Map.of(
                "error", true,
                "root", searchRoot.toString()
            ));
        }

        List<CodeSearchHit> hits = new ArrayList<>();
        int scannedFiles = 0;
        int skippedFiles = 0;
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            for (Path filePath : stream.toList()) {
                if (hits.size() >= maxResults) {
                    break;
                }
                if (!Files.isRegularFile(filePath)) {
                    continue;
                }
                if (isExcluded(filePath) || !isAllowedCodeFile(filePath)) {
                    continue;
                }
                scannedFiles++;
                long fileSize = safeFileSize(filePath);
                if (fileSize > maxFileSizeBytes) {
                    skippedFiles++;
                    continue;
                }
                try {
                    List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                    for (int index = 0; index < lines.size(); index++) {
                        String line = lines.get(index);
                        if (containsAllTerms(line, terms)) {
                            hits.add(new CodeSearchHit(relativizePath(filePath), index + 1, line.strip()));
                            if (hits.size() >= maxResults) {
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // 跳过单文件读取异常，保持整次检索可用。
                }
            }
        } catch (Exception exception) {
            return new ChatMcpToolResult(toolId(), "代码检索执行失败，请稍后重试", Map.of(
                "error", true,
                "message", StrUtil.blankToDefault(exception.getMessage(), "unknown"),
                "root", searchRoot.toString()
            ));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("root", searchRoot.toString());
        metadata.put("terms", terms);
        metadata.put("hits", hits.size());
        metadata.put("scannedFiles", scannedFiles);
        metadata.put("skippedFiles", skippedFiles);
        metadata.put("maxResults", maxResults);
        metadata.put("maxFileSizeBytes", maxFileSizeBytes);

        if (hits.isEmpty()) {
            return new ChatMcpToolResult(toolId(), "未找到匹配代码，请尝试更具体的关键词", metadata);
        }
        return new ChatMcpToolResult(toolId(), buildResultContent(terms, hits), metadata);
    }

    /**
     * 从自然语言中抽取检索词，支持“查找 xxx”“搜索 xxx”类表达。
     *
     * @param question 用户问题。
     * @return 关键词列表。
     */
    private List<String> parseSearchTerms(String question) {
        String normalized = StrUtil.trimToEmpty(question)
            .replace("，", " ")
            .replace("。", " ")
            .replace("？", " ")
            .replace("?", " ");
        if (StrUtil.isBlank(normalized)) {
            return List.of();
        }

        String extracted = ReUtil.get("(?:查找|搜索|检索|找到|找下|找一下)\\s+(.+)", normalized, 1);
        String searchPayload = StrUtil.blankToDefault(extracted, normalized);

        String[] tokens = searchPayload.split("\\s+");
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : tokens) {
            String cleaned = token.strip();
            if (StrUtil.isBlank(cleaned)) {
                continue;
            }
            if ("代码".equals(cleaned) || "实现".equals(cleaned) || "内容".equals(cleaned)) {
                continue;
            }
            terms.add(cleaned);
            if (terms.size() >= 3) {
                break;
            }
        }
        return new ArrayList<>(terms);
    }

    /**
     * 判断命中行是否包含所有关键词，保证结果相关性。
     *
     * @param line 代码行。
     * @param terms 关键词列表。
     * @return 命中返回 true。
     */
    private boolean containsAllTerms(String line, List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return false;
        }
        String normalizedLine = StrUtil.nullToEmpty(line).toLowerCase();
        return terms.stream().allMatch(term -> normalizedLine.contains(term.toLowerCase()));
    }

    /**
     * 生成检索可读输出。
     *
     * @param terms 关键词。
     * @param hits 命中列表。
     * @return 文本结果。
     */
    private String buildResultContent(List<String> terms, List<CodeSearchHit> hits) {
        StringBuilder builder = new StringBuilder();
        builder.append("【代码检索结果】\n\n");
        builder.append("关键词: ").append(String.join(" ", terms)).append("\n");
        builder.append("命中数量: ").append(hits.size()).append("\n\n");
        for (int i = 0; i < hits.size(); i++) {
            CodeSearchHit hit = hits.get(i);
            builder.append(i + 1)
                .append(". ")
                .append(hit.relativePath())
                .append(":")
                .append(hit.lineNumber())
                .append("\n")
                .append("   ")
                .append(hit.snippet())
                .append("\n");
        }
        return builder.toString().trim();
    }

    /**
     * 判断当前文件是否属于排除目录。
     *
     * @param filePath 文件路径。
     * @return 排除返回 true。
     */
    private boolean isExcluded(Path filePath) {
        for (Path part : filePath) {
            if (EXCLUDED_DIRECTORIES.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为允许扫描的代码文件。
     *
     * @param filePath 文件路径。
     * @return 允许返回 true。
     */
    private boolean isAllowedCodeFile(Path filePath) {
        String extension = FileUtil.extName(filePath.toString());
        return StrUtil.isNotBlank(extension) && ALLOWED_EXTENSIONS.contains(extension.toLowerCase());
    }

    /**
     * 安全读取文件大小，异常时返回 0。
     *
     * @param filePath 文件路径。
     * @return 文件大小。
     */
    private long safeFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /**
     * 将绝对路径转为相对路径，便于结果展示。
     *
     * @param filePath 文件路径。
     * @return 相对路径字符串。
     */
    private String relativizePath(Path filePath) {
        try {
            return searchRoot.relativize(filePath).toString().replace('\\', '/');
        } catch (Exception ignored) {
            return filePath.toString().replace('\\', '/');
        }
    }

    /**
     * 解析检索根目录，为空时自动回退到项目根。
     *
     * @param configuredRoot 配置目录。
     * @return 规范化路径。
     */
    private static Path resolveSearchRoot(String configuredRoot) {
        String candidate = StrUtil.trimToEmpty(configuredRoot);
        if (StrUtil.isBlank(candidate)) {
            Path current = Paths.get("").toAbsolutePath().normalize();
            if (StrUtil.equalsIgnoreCase(current.getFileName() == null ? "" : current.getFileName().toString(), "backend")) {
                return current.getParent() == null ? current : current.getParent();
            }
            return current;
        }
        return Paths.get(candidate).toAbsolutePath().normalize();
    }

    /**
     * 归一化最大返回数量。
     *
     * @param configured 配置值。
     * @return 合法结果上限。
     */
    private static int normalizeMaxResults(int configured) {
        if (configured <= 0) {
            return 20;
        }
        return Math.min(configured, 50);
    }

    /**
     * 归一化单文件最大扫描字节数。
     *
     * @param configured 配置值。
     * @return 合法字节上限。
     */
    private static long normalizeMaxFileSize(long configured) {
        if (configured <= 0) {
            return 1024 * 1024L;
        }
        return configured;
    }

    /**
     * 单条代码命中信息。
     *
     * @param relativePath 相对路径。
     * @param lineNumber 行号。
     * @param snippet 命中片段。
     */
    private record CodeSearchHit(String relativePath, int lineNumber, String snippet) {
    }
}
