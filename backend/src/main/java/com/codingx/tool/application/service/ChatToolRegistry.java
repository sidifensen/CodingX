package com.codingx.tool.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 负责按工具编码查找 chat_tool 内置执行器。
 */
@Component
@RequiredArgsConstructor
public class ChatToolRegistry {

    private final List<ChatToolExecutor> executors;
    private final Map<String, ChatToolExecutor> executorByCode = new LinkedHashMap<>();

    /**
     * 启动时构建工具编码索引，避免每次线性扫描。
     */
    @PostConstruct
    void init() {
        executorByCode.clear();
        for (ChatToolExecutor executor : executors) {
            if (executor == null || executor.toolCodes() == null) {
                continue;
            }
            for (String toolCode : executor.toolCodes()) {
                String normalizedCode = normalizeCode(toolCode);
                if (StrUtil.isBlank(normalizedCode)) {
                    continue;
                }
                if (executorByCode.containsKey(normalizedCode)) {
                    throw new IllegalStateException(
                        ErrorMessageCatalog.CHAT_TOOL_DUPLICATE_EXECUTOR_PREFIX + normalizedCode
                    );
                }
                executorByCode.put(normalizedCode, executor);
            }
        }
    }

    /**
     * 获取指定工具编码对应执行器。
     * @param toolCode 工具编码。
     * @return 执行器。
     */
    public ChatToolExecutor require(String toolCode) {
        String normalizedCode = normalizeCode(toolCode);
        ChatToolExecutor executor = executorByCode.get(normalizedCode);
        if (executor == null) {
            throw new BusinessException("CHAT_TOOL_EXECUTOR_NOT_FOUND", ErrorMessageCatalog.CHAT_TOOL_EXECUTOR_NOT_FOUND);
        }
        return executor;
    }

    /**
     * 返回当前已注册的工具编码。
     * @return 工具编码列表。
     */
    public List<String> allToolCodes() {
        return new ArrayList<>(executorByCode.keySet());
    }

    /**
     * 判断指定工具是否已接入执行器。
     * @param toolCode 工具编码。
     * @return 已接入返回 true。
     */
    public boolean hasExecutor(String toolCode) {
        return executorByCode.containsKey(normalizeCode(toolCode));
    }

    private String normalizeCode(String toolCode) {
        return StrUtil.trimToEmpty(toolCode).toLowerCase(Locale.ROOT);
    }
}

