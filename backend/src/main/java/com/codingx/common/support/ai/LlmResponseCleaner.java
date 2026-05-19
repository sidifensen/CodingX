package com.codingx.common.support.ai;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

/**
 * 清理模型响应中的脏字符和异常空白，避免回放与落库污染。
 */
@Component
public class LlmResponseCleaner {

    /**
     * 清洗模型返回文本。
     * @param value 原始文本。
     * @return 清洗后文本。
     */
    public String clean(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\u0000", "");
        normalized = normalized.replaceAll("\\r\\n?", "\n");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return StrUtil.trim(normalized);
    }
}

