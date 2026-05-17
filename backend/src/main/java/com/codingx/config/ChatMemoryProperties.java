package com.codingx.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一管理聊天会话压缩相关配置，控制摘要触发与原文保留窗口。
 */
@Data
@ConfigurationProperties(prefix = "app.chat.memory")
public class ChatMemoryProperties {

    /**
     * 是否启用会话压缩（摘要 + 最近原文窗口）。
     */
    private boolean summaryEnabled = true;

    /**
     * 触发摘要生成的消息条数阈值。
     */
    private int summaryTriggerMessages = 12;

    /**
     * 参与入模的最近轮次窗口（user+assistant 为 1 轮）。
     */
    private int historyKeepTurns = 6;

    /**
     * 摘要内容最大字符数，防止摘要无限增长。
     */
    private int summaryMaxCharacters = 4000;
}
