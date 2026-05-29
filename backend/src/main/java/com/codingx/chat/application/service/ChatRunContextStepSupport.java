package com.codingx.chat.application.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingx.chat.domain.model.ChatExecutionStep;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 将一次聊天运行选择的能力上下文编码为 chat_execution_step 记录，替代旧 task 绑定表。
 */
public final class ChatRunContextStepSupport {

    public static final String STEP_TYPE = "runtime_context";
    private static final String STEP_TITLE = "运行上下文";
    private static final String STEP_STATUS = "COMPLETED";
    private static final long STEP_SEQUENCE_NO = 0L;

    private ChatRunContextStepSupport() {
    }

    /**
     * 构造或更新运行上下文步骤；已有步骤传入后会复用主键和创建时间。
     * @param runId 运行标识。
     * @param mcpCodes 本轮 MCP 编码。
     * @param skillCodes 本轮技能编码。
     * @param expertCode 本轮专家编码。
     * @param existingStep 已存在的上下文步骤。
     * @return 可直接保存的执行步骤。
     */
    public static ChatExecutionStep buildStep(
        Long runId,
        List<String> mcpCodes,
        List<String> skillCodes,
        String expertCode,
        ChatExecutionStep existingStep
    ) {
        LocalDateTime now = LocalDateTime.now();
        JSONObject metadata = JSONUtil.createObj()
            .set("mcpCodes", normalizeCodes(mcpCodes))
            .set("skillCodes", normalizeCodes(skillCodes))
            .set("expertCode", StrUtil.trimToNull(expertCode));
        return ChatExecutionStep.builder()
            .id(existingStep == null || existingStep.getId() == null ? IdUtil.getSnowflakeNextId() : existingStep.getId())
            .runId(runId)
            .stepType(STEP_TYPE)
            .stepTitle(STEP_TITLE)
            .stepStatus(STEP_STATUS)
            .sequenceNo(STEP_SEQUENCE_NO)
            .content("本轮能力上下文")
            .metadataJson(metadata.toString())
            .createdAt(existingStep == null || existingStep.getCreatedAt() == null ? now : existingStep.getCreatedAt())
            .updatedAt(now)
            .build();
    }

    /**
     * 从步骤列表里解析运行上下文，缺失或 JSON 损坏时返回空上下文。
     * @param steps 执行步骤列表。
     * @return 运行上下文。
     */
    public static RunContext parseContext(List<ChatExecutionStep> steps) {
        Optional<ChatExecutionStep> contextStep = findContextStep(steps);
        if (contextStep.isEmpty() || StrUtil.isBlank(contextStep.get().getMetadataJson())) {
            return RunContext.empty();
        }
        try {
            JSONObject metadata = JSONUtil.parseObj(contextStep.get().getMetadataJson());
            return new RunContext(
                readStringArray(metadata, "mcpCodes"),
                readStringArray(metadata, "skillCodes"),
                StrUtil.trimToNull(metadata.getStr("expertCode"))
            );
        } catch (Exception ignored) {
            return RunContext.empty();
        }
    }

    /**
     * 查找上下文步骤，统一隐藏步骤类型命名细节。
     * @param steps 执行步骤列表。
     * @return 上下文步骤。
     */
    public static Optional<ChatExecutionStep> findContextStep(List<ChatExecutionStep> steps) {
        if (CollUtil.isEmpty(steps)) {
            return Optional.empty();
        }
        return steps.stream()
            .filter(step -> StrUtil.equalsIgnoreCase(STEP_TYPE, step.getStepType()))
            .findFirst();
    }

    private static List<String> readStringArray(JSONObject metadata, String fieldName) {
        JSONArray array = metadata.getJSONArray(fieldName);
        if (array == null || array.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object item : array) {
            String value = StrUtil.trimToEmpty(String.valueOf(item));
            if (StrUtil.isNotBlank(value)) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> normalizeCodes(List<String> codes) {
        if (CollUtil.isEmpty(codes)) {
            return List.of();
        }
        LinkedHashSet<String> normalizedCodes = new LinkedHashSet<>();
        for (String code : codes) {
            if (StrUtil.isNotBlank(code)) {
                normalizedCodes.add(code.trim());
            }
        }
        return List.copyOf(normalizedCodes);
    }

    /**
     * 表示一次运行选择的能力上下文。
     * @param mcpCodes MCP 编码。
     * @param skillCodes 技能编码。
     * @param expertCode 专家编码。
     */
    public record RunContext(List<String> mcpCodes, List<String> skillCodes, String expertCode) {
        public static RunContext empty() {
            return new RunContext(List.of(), List.of(), null);
        }
    }
}
