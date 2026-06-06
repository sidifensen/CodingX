package com.codingx.tool.application.service;

import cn.hutool.core.util.StrUtil;
import java.util.LinkedHashMap;
import java.util.Map;
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
     * 本地工具别名服务，用于把 Claude Code 风格工具名归一到真实执行器编码。
     */
    private final LocalToolAliasService localToolAliasService;

    /**
     * 执行指定工具编码。
     * @param toolCode 工具编码。
     * @param question 测试问题。
     * @return 执行结果。
     */
    public ChatToolExecutionResult execute(String toolCode, String question) {
        // 步骤 1：缺省调用内容使用统一探测问题，避免执行器收到空字符串后无法生成可读状态。
        String normalizedQuestion = StrUtil.blankToDefault(question, "请返回当前工具状态");
        // 步骤 2：模型可能按 Claude Code 风格传入别名，进入注册表前必须转换为真实短工具编码。
        String canonicalToolCode = localToolAliasService.toCanonicalCode(toolCode);
        // 步骤 3：从注册表强制获取执行器并执行，未注册工具由注册表抛出统一异常。
        ChatToolExecutionResult result = chatToolRegistry.require(canonicalToolCode).execute(canonicalToolCode, normalizedQuestion);
        return withAliasMetadata(result, toolCode, canonicalToolCode);
    }

    /**
     * 在工具结果中补充入口归一化信息，供前端、CLI 和模型证据区同时展示请求名与真实执行器编码。
     */
    private ChatToolExecutionResult withAliasMetadata(
        ChatToolExecutionResult result,
        String requestedToolCode,
        String canonicalToolCode
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (result.metadata() != null) {
            metadata.putAll(result.metadata());
        }
        metadata.put("requestedToolCode", StrUtil.blankToDefault(requestedToolCode, canonicalToolCode));
        metadata.put("canonicalToolCode", canonicalToolCode);
        return new ChatToolExecutionResult(result.toolCode(), result.content(), metadata);
    }
}

