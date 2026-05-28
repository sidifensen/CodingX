package com.codingx.config;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 负责配置 RuntimeProperties 所需的 Spring Bean 与基础设施。
 */
@Data
@ConfigurationProperties(prefix = "app.runtime")
public class RuntimeProperties {

    /**
     * 是否启用 Redis 运行态存储，用于跨节点取消控制。
     */
    private boolean useRedisStateStore = false;

    /**
     * 是否启用 Redis 队列门控，用于跨节点统一并发控制。
     */
    private boolean useRedisQueueGate = false;

    /**
     * 聊天链路允许的最大并发数。
     */
    private int queueMaxConcurrent = 2;

    /**
     * 单次排队获取执行资格的最长等待毫秒数。
     */
    private long queueAcquireTimeoutMs = 3000L;

    /**
     * 排队轮询的基础间隔毫秒数。
     */
    private long queuePollIntervalMs = 200L;

    /**
     * Redis 执行资格租约时长（秒），超时后视为失效。
     */
    private long queueLeaseSeconds = 300L;

    /**
     * 租约续期任务执行间隔毫秒数，避免长会话被租约误回收。
     */
    private long queueLeaseRenewIntervalMs = 10000L;

    /**
     * 代码检索工具扫描根目录；为空时自动回退到项目根目录。
     */
    private String codeSearchRoot = "";

    /**
     * 代码检索工具单次最大返回命中数。
     */
    private int codeSearchMaxResults = 20;

    /**
     * 代码检索工具单文件最大扫描大小（字节）。
     */
    private long codeSearchMaxFileSizeBytes = 1024 * 1024L;

    /**
     * 聊天附件上传单文件最大字节数，由业务层进行二次校验。
     */
    private long uploadMaxFileSizeBytes = 10L * 1024L * 1024L;

    /**
     * 联网搜索通道配置，默认关闭，避免开发环境误发外部检索请求。
     */
    private WebSearchProperties webSearch = new WebSearchProperties();

    /**
     * 定义联网搜索 provider 的基础配置。
     */
    @Data
    public static class WebSearchProperties {

        /**
         * 是否启用真实联网搜索。
         */
        private boolean enabled = false;

        /**
         * 搜索 provider 编码，目前支持 serper 与 tavily。
         */
        private String provider = "serper";

        /**
         * 搜索接口地址。
         */
        private String baseUrl = "";

        /**
         * 搜索服务 API Key。
         */
        private String apiKey = "";

        /**
         * 单次请求最大结果数。
         */
        private int maxResults = 5;

        /**
         * 搜索语言代码。
         */
        private String language = "zh-cn";

        /**
         * 搜索地区代码。
         */
        private String country = "cn";
    }
}
