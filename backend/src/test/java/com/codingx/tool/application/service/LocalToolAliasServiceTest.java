package com.codingx.tool.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 验证 Claude Code 风格工具别名和 CodingX 既有短工具编码之间的稳定映射。
 */
class LocalToolAliasServiceTest {

    /**
     * 六个 Claude Code 风格工具名仅作为兼容入口，执行时仍回落到既有短工具编码。
     */
    @Test
    void shouldMapClaudeCodeAliasesToCanonicalToolCodes() {
        LocalToolAliasService aliasService = new LocalToolAliasService();

        assertEquals("read", aliasService.toCanonicalCode("ReadFile"));
        assertEquals("write", aliasService.toCanonicalCode("WriteFile"));
        assertEquals("edit", aliasService.toCanonicalCode("EditFile"));
        assertEquals("bash", aliasService.toCanonicalCode("Bash"));
        assertEquals("find", aliasService.toCanonicalCode("Glob"));
        assertEquals("grep", aliasService.toCanonicalCode("Grep"));
    }

    /**
     * 旧的 OpenClaw 短工具名仍然是合法入口，并按大小写无关规则规范化。
     */
    @Test
    void shouldKeepLegacyShortToolNamesCallable() {
        LocalToolAliasService aliasService = new LocalToolAliasService();

        assertEquals("read", aliasService.toCanonicalCode("read"));
        assertEquals("read", aliasService.toCanonicalCode("READ"));
        assertEquals("custom_tool", aliasService.toCanonicalCode(" Custom_Tool "));
    }

    /**
     * 展示名只影响过程时间线和诊断展示，不能改变未知工具的真实编码。
     */
    @Test
    void shouldResolveDisplayNameForKnownCanonicalTool() {
        LocalToolAliasService aliasService = new LocalToolAliasService();

        assertEquals("ReadFile", aliasService.toDisplayName("read"));
        assertEquals("Bash", aliasService.toDisplayName("bash"));
        assertEquals("unknown", aliasService.toDisplayName("unknown"));
    }
}
