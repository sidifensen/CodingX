package com.codingx.chat.domain.model;

import java.util.Locale;

/**
 * 定义目标步骤状态，步骤状态来自目标工具的 steps 入参并独立落库。
 */
public enum ChatGoalStepStatus {
    /** 步骤尚未开始。 */
    PENDING,
    /** 步骤正在进行。 */
    IN_PROGRESS,
    /** 步骤已经完成。 */
    COMPLETED,
    /** 步骤被阻塞。 */
    BLOCKED,
    /** 步骤已取消。 */
    CANCELLED;

    /**
     * 将模型输入的步骤状态规范化为后端枚举，兼容常见同义写法。
     *
     * @param value 模型或调用方传入的状态文本。
     * @param fallback 输入为空或无法识别时使用的兜底状态。
     * @return 规范化后的步骤状态。
     */
    public static ChatGoalStepStatus normalize(String value, ChatGoalStepStatus fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PENDING", "TODO" -> PENDING;
            case "IN_PROGRESS", "ACTIVE", "RUNNING" -> IN_PROGRESS;
            case "COMPLETED", "COMPLETE", "DONE", "SUCCESS" -> COMPLETED;
            case "BLOCKED", "FAILED" -> BLOCKED;
            case "CANCELLED", "CANCELED", "CANCEL" -> CANCELLED;
            default -> fallback;
        };
    }
}
