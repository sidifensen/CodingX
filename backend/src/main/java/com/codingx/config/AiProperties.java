package com.codingx.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 统一管理聊天模型路由所需的 AI 配置，兼容旧单模型配置并扩展为多候选模型池。
 */
@Data
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /**
     * 旧式默认 provider 名称，未显式配置候选池时用于合成默认模型候选。
     */
    private String provider = "deepseek";

    /**
     * 旧式默认 provider 基础地址。
     */
    private String baseUrl = "https://api.deepseek.com/v1";

    /**
     * 旧式默认 provider API Key。
     */
    private String apiKey = "";

    /**
     * 旧式默认聊天模型名，仅作为历史兼容回退。
     */
    private String chatModel = "deepseek-chat";

    /**
     * HTTP 连接超时时间。
     */
    private int connectTimeoutMs = 10_000;

    /**
     * HTTP 读取超时时间。
     */
    private int readTimeoutMs = 120_000;

    /**
     * 默认系统提示词。
     */
    private String systemPrompt = "You are the CodingX assistant. Be concise, practical, and safe.";

    /**
     * 多 provider 配置映射。
     */
    private Map<String, Provider> providers = new HashMap<>();

    /**
     * 聊天模型组配置。
     */
    private ChatModelGroup chat = new ChatModelGroup();

    /**
     * 模型选择与熔断策略配置。
     */
    private Selection selection = new Selection();

    /**
     * 单个 provider 的连接配置。
     */
    @Data
    public static class Provider {

        /**
         * provider 基础地址。
         */
        private String baseUrl;

        /**
         * provider API Key。
         */
        private String apiKey;

        /**
         * provider 自定义端点定义。
         */
        private Map<String, String> endpoints = new HashMap<>();
    }

    /**
     * 聊天模型组，描述默认模型、深度思考模型和候选列表。
     */
    @Data
    public static class ChatModelGroup {
        /**
         * 历史兼容字段：保留旧测试与旧对象装配路径，但运行时不再依赖该值选模型。
         */
        private String defaultModel;

        /**
         * 历史兼容字段：保留旧测试与旧对象装配路径，但运行时不再依赖该值选模型。
         */
        private String deepThinkingModel;

        /**
         * 聊天候选模型列表。
         */
        private List<ChatCandidate> candidates = new ArrayList<>();
    }

    /**
     * 单个聊天模型候选定义。
     */
    @Data
    public static class ChatCandidate {

        /**
         * 候选唯一 ID。
         */
        private String id;

        /**
         * 所属 provider。
         */
        private String provider;

        /**
         * provider 侧模型名称。
         */
        private String model;

        /**
         * 路由优先级，越小越优先。
         */
        private Integer priority = 100;

        /**
         * 是否启用当前候选。
         */
        private Boolean enabled = true;

        /**
         * 是否支持思考模式。
         */
        private Boolean supportsThinking = false;

        /**
         * 是否支持视觉理解能力。
         * 说明：图片附件路由会优先挑选标记为视觉模型的候选，避免把图片误发给纯文本模型。
         */
        private Boolean supportsVision = false;
    }

    /**
     * 熔断与首包等待策略。
     */
    @Data
    public static class Selection {

        /**
         * 连续失败阈值。
         */
        private Integer failureThreshold = 2;

        /**
         * 熔断打开时长。
         */
        private Long openDurationMs = 30_000L;

        /**
         * 首包等待超时时间；超过该窗口会切换候选，不能等到底层读超时才释放用户请求。
         */
        private Long firstPacketTimeoutMs = 15_000L;

        /**
         * 首包成功后的流式空闲超时时间；表示允许的最长连续静默窗口，流持续产出事件时不会触发，
         * 连续静默超过该窗口才取消 provider 并让上层收口失败状态。
         */
        private Long streamCompletionTimeoutMs = 300_000L;
    }
}
