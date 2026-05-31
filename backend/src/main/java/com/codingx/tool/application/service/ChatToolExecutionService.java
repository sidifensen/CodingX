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

    /**
     * 工具注册表，负责按工具编码查找真实执行器。
     */
    private final ChatToolRegistry chatToolRegistry;

    /**
     * 执行指定工具编码。
     * @param toolCode 工具编码。
     * @param question 测试问题。
     * @return 执行结果。
     */
    public ChatToolExecutionResult execute(String toolCode, String question) {
        // 步骤 1：缺省调用内容使用统一探测问题，避免执行器收到空字符串后无法生成可读状态。
        String normalizedQuestion = StrUtil.blankToDefault(question, "请返回当前工具状态");
        // 步骤 2：从注册表强制获取执行器并执行，未注册工具由注册表抛出统一异常。
        return chatToolRegistry.require(toolCode).execute(toolCode, normalizedQuestion);
    }
}

