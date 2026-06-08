package com.codingx.chat.domain.model;

import java.util.Locale;

/**
 * 定义聊天目标的主状态，状态由模型工具显式更新，不再从 executionSteps 推导。
 */
public enum ChatGoalStatus {
    /** 目标正在进行，active goal 查询只返回该状态。 */
    ACTIVE,
    /** 目标已完成，刷新后不再作为 active goal 常驻展示。 */
    COMPLETED,
    /** 目标被阻塞，需要用户或下游条件解除后才能继续。 */
    BLOCKED,
    /** 目标已取消，保留历史事件但不再参与 active 查询。 */
    CANCELLED;

    /**
     * 将模型输入的状态文本规范化为后端枚举，兼容小写和 done/canceled 等常见别名。
     *
     * @param value 模型或调用方传入的状态文本。
     * @param fallback 输入为空或无法识别时使用的兜底状态。
     * @return 规范化后的目标状态。
     */
    public static ChatGoalStatus normalize(String value, ChatGoalStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVE", "IN_PROGRESS", "RUNNING" -> ACTIVE;
            case "COMPLETED", "COMPLETE", "DONE", "SUCCESS" -> COMPLETED;
            case "BLOCKED", "FAILED" -> BLOCKED;
            case "CANCELLED", "CANCELED", "CANCEL" -> CANCELLED;
            default -> fallback;
        };
    }
}
