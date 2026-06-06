package com.codingx.tool.application.service;

import cn.hutool.core.util.StrUtil;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 维护模型可见的 Claude Code 风格工具名与后端真实本地工具编码之间的映射。
 * 业务约束：别名只影响模型 schema、过程展示和入口归一化，真实执行仍委托给既有短工具编码。
 */
@Service
public class LocalToolAliasService {

    /** Claude Code 风格展示名到 CodingX 既有执行器编码的映射，保持声明顺序用于 schema 输出。 */
    private static final Map<String, String> ALIAS_TO_CANONICAL = createAliasMap();
    /** 既有执行器编码到 Claude Code 风格展示名的反向映射，用于时间线和文档展示。 */
    private static final Map<String, String> CANONICAL_TO_ALIAS = createCanonicalMap();

    /**
     * 将模型或用户传入的工具编码转换为真实执行器编码。
     * @param toolCode 模型可见别名或既有短工具编码。
     * @return 可交给执行器注册表查找的规范化编码。
     */
    public String toCanonicalCode(String toolCode) {
        String trimmedCode = StrUtil.trimToEmpty(toolCode);
        if (StrUtil.isBlank(trimmedCode)) {
            return "";
        }
        String directAliasMatch = ALIAS_TO_CANONICAL.get(trimmedCode);
        if (directAliasMatch != null) {
            return directAliasMatch;
        }
        return trimmedCode.toLowerCase(Locale.ROOT);
    }

    /**
     * 解析真实执行器编码对应的模型展示名。
     * @param canonicalToolCode 真实执行器编码。
     * @return 已知六大工具返回 Claude Code 风格展示名，其他工具保持原编码。
     */
    public String toDisplayName(String canonicalToolCode) {
        String normalizedCode = StrUtil.trimToEmpty(canonicalToolCode).toLowerCase(Locale.ROOT);
        return StrUtil.blankToDefault(CANONICAL_TO_ALIAS.get(normalizedCode), normalizedCode);
    }

    /**
     * 判断指定真实执行器编码是否存在 Claude Code 风格别名。
     * @param canonicalToolCode 真实执行器编码。
     * @return 存在别名返回 true。
     */
    public boolean hasAliasForCanonicalCode(String canonicalToolCode) {
        return CANONICAL_TO_ALIAS.containsKey(StrUtil.trimToEmpty(canonicalToolCode).toLowerCase(Locale.ROOT));
    }

    /**
     * 返回别名到真实编码的有序快照，供模型 schema 生成时追加别名工具。
     * @return 有序不可变映射。
     */
    public Map<String, String> aliasMappings() {
        return Map.copyOf(ALIAS_TO_CANONICAL);
    }

    private static Map<String, String> createAliasMap() {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("ReadFile", "read");
        aliases.put("WriteFile", "write");
        aliases.put("EditFile", "edit");
        aliases.put("Bash", "bash");
        aliases.put("Glob", "find");
        aliases.put("Grep", "grep");
        return aliases;
    }

    private static Map<String, String> createCanonicalMap() {
        Map<String, String> canonicalMap = new LinkedHashMap<>();
        ALIAS_TO_CANONICAL.forEach((alias, canonicalCode) -> canonicalMap.put(canonicalCode, alias));
        return canonicalMap;
    }
}
