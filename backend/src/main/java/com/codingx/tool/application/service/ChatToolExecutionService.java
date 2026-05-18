package com.codingx.tool.application.service;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 负责统一执行 chat_tool 内置工具。
 */
@Service
@RequiredArgsConstructor
public class ChatToolExecutionService {

    private final ChatToolRegistry chatToolRegistry;

    /**
     * 执行指定工具编码。
     * @param toolCode 工具编码。
     * @param question 测试问题。
     * @return 执行结果。
     */
    public ChatToolExecutionResult execute(String toolCode, String question) {
        String normalizedQuestion = StrUtil.blankToDefault(question, "请返回当前工具状态");
        return chatToolRegistry.require(toolCode).execute(toolCode, normalizedQuestion);
    }
}

