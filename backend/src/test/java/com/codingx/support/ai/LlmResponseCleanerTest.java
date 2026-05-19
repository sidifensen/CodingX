package com.codingx.common.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 验证模型响应清洗器会移除脏字符与异常空白。
 */
class LlmResponseCleanerTest {

    /**
     * 响应文本中的 NUL 字符和多余空行应被清洗。
     */
    @Test
    void cleanRemovesControlCharactersAndCollapsesBlankLines() {
        LlmResponseCleaner cleaner = new LlmResponseCleaner();

        String cleaned = cleaner.clean("  第一行\u0000\n\n\n第二行  ");

        assertEquals("第一行\n\n第二行", cleaned);
    }
}
