package com.codingx.chat.application.service;

import cn.hutool.core.util.StrUtil;
import com.codingx.chat.application.service.support.ConfigCryptoService;
import com.codingx.chat.domain.model.ChatRuntimeSetting;
import com.codingx.chat.domain.repository.ChatRuntimeSettingRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.config.AiProperties;
import com.codingx.config.ChatExecutorRuntimeProperties;
import com.codingx.config.ChatMemoryProperties;
import com.codingx.config.RuntimeProperties;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 提供统一运行时配置读取入口，负责合并数据库覆盖值与默认配置值并做内存缓存。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuntimeSettingService {

    private static final String TYPE_BOOLEAN = "BOOLEAN";
    private static final String TYPE_INTEGER = "INTEGER";
    private static final String TYPE_LONG = "LONG";
    private static final String TYPE_DECIMAL = "DECIMAL";
    private static final String TYPE_STRING = "STRING";

    private final ChatRuntimeSettingRepository chatRuntimeSettingRepository;
    private final RuntimeProperties runtimeProperties;
    private final ChatExecutorRuntimeProperties chatExecutorRuntimeProperties;
    private final ChatMemoryProperties chatMemoryProperties;
    private final AiProperties aiProperties;
    private final ConfigCryptoService configCryptoService;
    private final Map<String, ChatRuntimeSetting> cache = new ConcurrentHashMap<>();

    /**
     * 启动后预加载全量配置，避免首次读取时多次命中数据库。
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 刷新运行时配置缓存。
     */
    public synchronized void refresh() {
        Map<String, ChatRuntimeSetting> next = new ConcurrentHashMap<>();
        List<ChatRuntimeSetting> settings = chatRuntimeSettingRepository.findAll();
        for (ChatRuntimeSetting setting : settings) {
            if (setting != null && StrUtil.isNotBlank(setting.getSettingKey())) {
                next.put(setting.getSettingKey(), setting);
            }
        }
        cache.clear();
        cache.putAll(next);
        log.info("Runtime settings cache refreshed, size={}", cache.size());
    }

    /**
     * 返回当前缓存的全量配置，供管理端按分类展示。
     * @return 全量配置列表。
     */
    public List<ChatRuntimeSetting> listAll() {
        return cache.values().stream()
            .sorted((left, right) -> {
                int categoryCompare = StrUtil.compareIgnoreCase(
                    StrUtil.blankToDefault(left.getCategoryCode(), ""),
                    StrUtil.blankToDefault(right.getCategoryCode(), ""),
                    true
                );
                if (categoryCompare != 0) {
                    return categoryCompare;
                }
                int sortCompare = Integer.compare(
                    left.getSortNo() == null ? 0 : left.getSortNo(),
                    right.getSortNo() == null ? 0 : right.getSortNo()
                );
                if (sortCompare != 0) {
                    return sortCompare;
                }
                return StrUtil.compareIgnoreCase(
                    StrUtil.blankToDefault(left.getSettingKey(), ""),
                    StrUtil.blankToDefault(right.getSettingKey(), ""),
                    true
                );
            })
            .toList();
    }

    /**
     * 获取布尔配置，未配置时回退默认值。
     * @param key 配置键。
     * @param fallback 回退值。
     * @return 布尔值。
     */
    public boolean getBoolean(String key, boolean fallback) {
        String raw = rawValue(key);
        if (StrUtil.isBlank(raw)) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase();
        if ("true".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized)) {
            return false;
        }
        throw invalidValue(key, raw, TYPE_BOOLEAN);
    }

    /**
     * 获取整数配置，未配置时回退默认值。
     * @param key 配置键。
     * @param fallback 回退值。
     * @return 整数值。
     */
    public int getInt(String key, int fallback) {
        String raw = rawValue(key);
        if (StrUtil.isBlank(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalidValue(key, raw, TYPE_INTEGER);
        }
    }

    /**
     * 获取长整型配置，未配置时回退默认值。
     * @param key 配置键。
     * @param fallback 回退值。
     * @return 长整型值。
     */
    public long getLong(String key, long fallback) {
        String raw = rawValue(key);
        if (StrUtil.isBlank(raw)) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalidValue(key, raw, TYPE_LONG);
        }
    }

    /**
     * 获取小数配置，未配置时回退默认值，主要用于模型阈值等运行时策略参数。
     * @param key 配置键。
     * @param fallback 回退值。
     * @return 小数值。
     */
    public double getDouble(String key, double fallback) {
        String raw = rawValue(key);
        if (StrUtil.isBlank(raw)) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalidValue(key, raw, TYPE_DECIMAL);
        }
    }

    /**
     * 获取字符串配置，未配置时回退默认值。
     * @param key 配置键。
     * @param fallback 回退值。
     * @return 字符串值。
     */
    public String getString(String key, String fallback) {
        String raw = rawValue(key);
        return StrUtil.isBlank(raw) ? fallback : raw.trim();
    }

    /**
     * 按前缀返回解密后的配置键值映射，供候选池与 provider endpoint 这类层级配置聚合使用。
     * @param prefix 配置前缀。
     * @return 命中的键值映射。
     */
    public Map<String, String> getByPrefix(String prefix) {
        if (StrUtil.isBlank(prefix)) {
            return Map.of();
        }
        Map<String, String> matched = new LinkedHashMap<>();
        cache.entrySet().stream()
            .filter(entry -> StrUtil.startWith(entry.getKey(), prefix))
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> matched.put(entry.getKey(), rawValue(entry.getKey())));
        return matched;
    }

    /**
     * 获取会话摘要开关。
     * @return 是否启用摘要。
     */
    public boolean summaryEnabled() {
        return getBoolean("chat.memory.summary_enabled", chatMemoryProperties.isSummaryEnabled());
    }

    /**
     * 获取摘要触发消息条数阈值。
     * @return 触发阈值。
     */
    public int summaryTriggerMessages() {
        return getInt("chat.memory.summary_trigger_messages", chatMemoryProperties.getSummaryTriggerMessages());
    }

    /**
     * 获取摘要保留轮次。
     * @return 保留轮次。
     */
    public int historyKeepTurns() {
        return getInt("chat.memory.history_keep_turns", chatMemoryProperties.getHistoryKeepTurns());
    }

    /**
     * 获取摘要最大字符数。
     * @return 最大字符数。
     */
    public int summaryMaxCharacters() {
        return getInt("chat.memory.summary_max_characters", chatMemoryProperties.getSummaryMaxCharacters());
    }

    /**
     * 获取搜索结果 TopK。
     * @return TopK。
     */
    public int searchTopK() {
        return getInt("search.top_k", 5);
    }

    /**
     * 获取搜索重排开关。
     * @return 是否启用重排。
     */
    public boolean searchRerankEnabled() {
        return getBoolean("search.rerank_enabled", true);
    }

    /**
     * 获取单次搜索超时时间毫秒。
     * @return 超时时间。
     */
    public long searchTimeoutMs() {
        return getLong("search.timeout_ms", 15_000L);
    }

    /**
     * 获取真实联网搜索开关。
     * @return 是否启用联网搜索。
     */
    public boolean webSearchEnabled() {
        RuntimeProperties.WebSearchProperties fallback = runtimeProperties.getWebSearch();
        return getBoolean("web_search.enabled", fallback != null && fallback.isEnabled());
    }

    /**
     * 获取联网搜索 provider 编码。
     * @return provider 编码。
     */
    public String webSearchProvider() {
        RuntimeProperties.WebSearchProperties fallback = runtimeProperties.getWebSearch();
        String fallbackValue = fallback == null ? "serper" : fallback.getProvider();
        return getString("web_search.provider", fallbackValue);
    }

    /**
     * 获取联网搜索接口地址。
     * @return 接口地址。
     */
    public String webSearchBaseUrl() {
        RuntimeProperties.WebSearchProperties fallback = runtimeProperties.getWebSearch();
        String fallbackValue = fallback == null ? "" : fallback.getBaseUrl();
        return getString("web_search.base_url", fallbackValue);
    }

    /**
     * 获取联网搜索 API Key。
     * @return API Key。
     */
    public String webSearchApiKey() {
        RuntimeProperties.WebSearchProperties fallback = runtimeProperties.getWebSearch();
        String fallbackValue = fallback == null ? "" : fallback.getApiKey();
        return getString("web_search.api_key", fallbackValue);
    }

    /**
     * 获取联网搜索最大候选数。
     * @return 最大候选数。
     */
    public int webSearchMaxResults() {
        RuntimeProperties.WebSearchProperties fallback = runtimeProperties.getWebSearch();
        int fallbackValue = fallback == null ? 5 : fallback.getMaxResults();
        return getInt("web_search.max_results", fallbackValue);
    }

    /**
     * 获取联网搜索语言代码。
     * @return 语言代码。
     */
    public String webSearchLanguage() {
        RuntimeProperties.WebSearchProperties fallback = runtimeProperties.getWebSearch();
        String fallbackValue = fallback == null ? "zh-cn" : fallback.getLanguage();
        return getString("web_search.language", fallbackValue);
    }

    /**
     * 获取联网搜索地区代码。
     * @return 地区代码。
     */
    public String webSearchCountry() {
        RuntimeProperties.WebSearchProperties fallback = runtimeProperties.getWebSearch();
        String fallbackValue = fallback == null ? "cn" : fallback.getCountry();
        return getString("web_search.country", fallbackValue);
    }

    /**
     * 获取搜索最大并发子问题数。
     * @return 并发子问题上限。
     */
    public int searchMaxParallelQuestions() {
        return getInt("search.max_parallel_questions", 3);
    }

    /**
     * 获取本地工具调用允许的最大轮次。
     * 这是防止模型反复工具调用不收敛的保护阈值，默认值调高到 10 轮。
     * @return 最大工具调用轮次。
     */
    public int chatToolMaxRounds() {
        return getInt("chat.tool.max_rounds", 10);
    }

    /**
     * 获取聊天歧义引导开关，默认沿用 ragent 的开启策略，避免模糊问题被直接硬答。
     * @return 是否启用歧义引导。
     */
    public boolean chatIntentGuidanceEnabled() {
        return getBoolean("chat.intent.guidance.enabled", true);
    }

    /**
     * 获取歧义引导分数比值阈值；第二名/第一名达到该值时直接触发澄清。
     * @return 分数比值阈值。
     */
    public double chatIntentGuidanceAmbiguityScoreRatio() {
        return getDouble("chat.intent.guidance.ambiguity_score_ratio", 0.8D);
    }

    /**
     * 获取歧义引导边界缓冲宽度；落入边界区间时交由 LLM 二次确认。
     * @return 边界缓冲宽度。
     */
    public double chatIntentGuidanceAmbiguityMargin() {
        return getDouble("chat.intent.guidance.ambiguity_margin", 0.15D);
    }

    /**
     * 获取单次歧义引导最多展示的候选数量，防止提示内容过长影响用户选择。
     * @return 最大候选数量。
     */
    public int chatIntentGuidanceMaxOptions() {
        return getInt("chat.intent.guidance.max_options", 6);
    }

    /**
     * 获取门控最大并发。
     * @return 最大并发。
     */
    public int queueMaxConcurrent() {
        return getInt("queue.max_concurrent", runtimeProperties.getQueueMaxConcurrent());
    }

    /**
     * 获取门控获取超时时间毫秒。
     * @return 获取超时时间。
     */
    public long queueAcquireTimeoutMs() {
        return getLong("queue.acquire_timeout_ms", runtimeProperties.getQueueAcquireTimeoutMs());
    }

    /**
     * 获取门控轮询间隔毫秒。
     * @return 轮询间隔。
     */
    public long queuePollIntervalMs() {
        return getLong("queue.poll_interval_ms", runtimeProperties.getQueuePollIntervalMs());
    }

    /**
     * 获取门控租约秒数。
     * @return 租约秒数。
     */
    public long queueLeaseSeconds() {
        return getLong("queue.lease_seconds", runtimeProperties.getQueueLeaseSeconds());
    }

    /**
     * 获取门控续租间隔毫秒。
     * @return 续租间隔。
     */
    public long queueLeaseRenewIntervalMs() {
        return getLong("queue.lease_renew_interval_ms", runtimeProperties.getQueueLeaseRenewIntervalMs());
    }

    /**
     * 获取代码检索根目录。
     * @return 根目录。
     */
    public String codeSearchRoot() {
        return getString("code_search.root", runtimeProperties.getCodeSearchRoot());
    }

    /**
     * 获取代码检索最大命中条数。
     * @return 最大条数。
     */
    public int codeSearchMaxResults() {
        return getInt("code_search.max_results", runtimeProperties.getCodeSearchMaxResults());
    }

    /**
     * 获取代码检索单文件最大扫描字节数。
     * @return 最大字节数。
     */
    public long codeSearchMaxFileSizeBytes() {
        return getLong("code_search.max_file_size_bytes", runtimeProperties.getCodeSearchMaxFileSizeBytes());
    }

    /**
     * 获取聊天附件上传单文件最大字节数。
     * @return 最大字节数。
     */
    public long chatAttachmentMaxFileSizeBytes() {
        return getLong("chat.attachment.max_file_size_bytes", runtimeProperties.getUploadMaxFileSizeBytes());
    }

    /**
     * 获取模型路由连续失败阈值。
     * @return 连续失败阈值。
     */
    public int aiFailureThreshold() {
        Integer fallback = aiProperties.getSelection() == null ? null : aiProperties.getSelection().getFailureThreshold();
        return getInt("ai.selection.failure_threshold", fallback == null ? 2 : fallback);
    }

    /**
     * 获取模型路由熔断打开时长毫秒。
     * @return 熔断时长毫秒。
     */
    public long aiOpenDurationMs() {
        Long fallback = aiProperties.getSelection() == null ? null : aiProperties.getSelection().getOpenDurationMs();
        return getLong("ai.selection.open_duration_ms", fallback == null ? 30_000L : fallback);
    }

    /**
     * 获取模型路由首包等待超时毫秒。
     * @return 首包等待超时毫秒。
     */
    public long aiFirstPacketTimeoutMs() {
        Long fallback = aiProperties.getSelection() == null ? null : aiProperties.getSelection().getFirstPacketTimeoutMs();
        return getLong("ai.selection.first_packet_timeout_ms", fallback == null ? 15_000L : fallback);
    }

    /**
     * 获取聊天入口线程池核心线程数。
     * @return 核心线程数。
     */
    public int chatExecutorStreamCorePoolSize() {
        return getInt("chat.executor.stream_core_pool_size", chatExecutorRuntimeProperties.getStreamCorePoolSize());
    }

    /**
     * 获取聊天入口线程池最大线程数。
     * @return 最大线程数。
     */
    public int chatExecutorStreamMaxPoolSize() {
        return getInt("chat.executor.stream_max_pool_size", chatExecutorRuntimeProperties.getStreamMaxPoolSize());
    }

    /**
     * 获取聊天入口线程池队列容量。
     * @return 队列容量。
     */
    public int chatExecutorStreamQueueCapacity() {
        return getInt("chat.executor.stream_queue_capacity", chatExecutorRuntimeProperties.getStreamQueueCapacity());
    }

    /**
     * 获取搜索线程池核心线程数。
     * @return 核心线程数。
     */
    public int chatExecutorSearchCorePoolSize() {
        return getInt("chat.executor.search_core_pool_size", chatExecutorRuntimeProperties.getSearchCorePoolSize());
    }

    /**
     * 获取搜索线程池最大线程数。
     * @return 最大线程数。
     */
    public int chatExecutorSearchMaxPoolSize() {
        return getInt("chat.executor.search_max_pool_size", chatExecutorRuntimeProperties.getSearchMaxPoolSize());
    }

    /**
     * 获取搜索线程池队列容量。
     * @return 队列容量。
     */
    public int chatExecutorSearchQueueCapacity() {
        return getInt("chat.executor.search_queue_capacity", chatExecutorRuntimeProperties.getSearchQueueCapacity());
    }

    /**
     * 获取聊天线程池保活秒数。
     * @return 保活秒数。
     */
    public long chatExecutorKeepAliveSeconds() {
        return getLong("chat.executor.keep_alive_seconds", chatExecutorRuntimeProperties.getKeepAliveSeconds());
    }

    /**
     * 返回原始配置值，未命中返回 null。
     * @param key 配置键。
     * @return 原始值。
     */
    private String rawValue(String key) {
        ChatRuntimeSetting setting = cache.get(key);
        if (setting == null) {
            return null;
        }
        if (Boolean.TRUE.equals(setting.getSecret())) {
            if (StrUtil.isBlank(setting.getEncryptedValue())) {
                throw new IllegalStateException("敏感配置缺少密文，key=" + key);
            }
            return configCryptoService.decrypt(setting.getEncryptedValue());
        }
        return setting.getSettingValue();
    }

    /**
     * 统一组装配置值格式非法异常。
     * @param key 配置键。
     * @param value 原始值。
     * @param type 期望类型。
     * @return 业务异常。
     */
    private BusinessException invalidValue(String key, String value, String type) {
        return new BusinessException(
            "SETTING_VALUE_INVALID",
            ErrorMessageCatalog.CHAT_SETTING_VALUE_INVALID_PREFIX + key
                + ErrorMessageCatalog.CHAT_SETTING_VALUE_INVALID_INFIX + value
                + ErrorMessageCatalog.CHAT_SETTING_VALUE_INVALID_SUFFIX + type + " 类型"
        );
    }
}
