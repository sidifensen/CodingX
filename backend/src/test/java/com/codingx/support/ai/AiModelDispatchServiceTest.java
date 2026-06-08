package com.codingx.common.support.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codingx.chat.domain.model.ChatMessage;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.config.AiProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 验证模型路由服务的候选选择、首包探测与 fallback 行为。
 */
class AiModelDispatchServiceTest {

    /**
     * 首个候选失败后应回退到下一个可用候选，并继续输出增量。
     */
    @Test
    void streamChatFallsBackToNextAvailableProvider() {
        RecordingProvider failingProvider = RecordingProvider.failing("primary", "deepseek-chat");
        RecordingProvider healthyProvider = RecordingProvider.success("secondary", "qwen-plus", List.of("hello", " world"));
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(failingProvider, healthyProvider),
            new AiProviderHealthRegistry(2, 30_000L),
            new AiModelSelector(minimalProperties())
        );
        AiConversationRequest request = AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .preferredModel("deepseek-chat")
            .stream(true)
            .build();
        List<String> deltas = new ArrayList<>();
        List<String> terminals = new ArrayList<>();

        service.streamChat(request, new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }

            @Override
            public void onComplete() {
                terminals.add("done");
            }

            @Override
            public void onMetadata(String provider, String model) {
                terminals.add(provider + ":" + model);
            }
        });

        assertEquals(List.of("primary", "secondary"), service.getLastAttemptedProviders());
        assertEquals(List.of("hello", " world"), deltas);
        assertEquals(List.of("secondary:qwen-plus", "done"), terminals);
    }

    /**
     * 没有任何候选成功时应抛出异常，而不是静默吞掉错误。
     */
    @Test
    void streamChatThrowsWhenAllProvidersFail() {
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(
                RecordingProvider.failing("primary", "deepseek-chat"),
                RecordingProvider.failing("secondary", "qwen-plus")
            ),
            new AiProviderHealthRegistry(2, 30_000L),
            new AiModelSelector(minimalProperties())
        );
        AiConversationRequest request = AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .preferredModel("deepseek-chat")
            .stream(true)
            .build();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.streamChat(request, new AiStreamHandler() {
        }));

        assertEquals(ErrorMessageCatalog.AI_NO_AVAILABLE_PROVIDER, exception.getMessage());
    }

    /**
     * 指定 preferredModel 时应优先尝试匹配候选，而不是单纯按 provider 注册顺序。
     */
    @Test
    void streamChatPrefersRequestedModelTarget() {
        RecordingProvider stubProvider = RecordingProvider.success("stub", "stub-chat", List.of("stub"));
        RecordingProvider primaryProvider = RecordingProvider.success("deepseek", "deepseek-chat", List.of("real"));
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(stubProvider, primaryProvider),
            new AiProviderHealthRegistry(2, 30_000L),
            minimalSelectorWithCandidates(minimalPropertiesWithCandidates(
                candidate("stub-chat", "stub", "stub-chat", 10, false),
                candidate("deepseek-chat", "deepseek", "deepseek-chat", 100, false)
            ))
        );

        List<String> deltas = new ArrayList<>();
        service.streamChat(AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .preferredModel("deepseek-chat")
            .stream(true)
            .build(), new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }
        });

        assertEquals(List.of("deepseek"), service.getLastAttemptedProviders());
        assertEquals(List.of("real"), deltas);
    }

    /**
     * 首包前出错时应回退到下一个候选，且不会把失败候选的缓冲内容泄漏给下游。
     */
    @Test
    void streamChatFallsBackWhenFirstProviderFailsBeforeFirstTokenHandshake() {
        RecordingProvider probeFailingProvider = RecordingProvider.probeFailure("primary", "deepseek-chat");
        RecordingProvider healthyProvider = RecordingProvider.success("secondary", "qwen-plus", List.of("clean"));
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(probeFailingProvider, healthyProvider),
            new AiProviderHealthRegistry(2, 30_000L),
            new AiModelSelector(minimalProperties())
        );
        List<String> deltas = new ArrayList<>();

        service.streamChat(AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .preferredModel("deepseek-chat")
            .stream(true)
            .build(), new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }
        });

        assertEquals(List.of("primary", "secondary"), service.getLastAttemptedProviders());
        assertEquals(List.of("clean"), deltas);
    }

    /**
     * 熔断的候选在冷却前不应继续参与调度。
     */
    @Test
    void streamChatSkipsOpenCircuitCandidate() {
        RecordingProvider failingProvider = RecordingProvider.failing("primary", "deepseek-chat");
        RecordingProvider healthyProvider = RecordingProvider.success("secondary", "qwen-plus", List.of("fallback"));
        AiProviderHealthRegistry registry = new AiProviderHealthRegistry(1, 30_000L);
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(failingProvider, healthyProvider),
            registry,
            new AiModelSelector(minimalProperties())
        );
        AiConversationRequest request = AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .preferredModel("deepseek-chat")
            .stream(true)
            .build();

        service.streamChat(request, new AiStreamHandler() {});
        assertFalse(registry.allowCall("deepseek-chat"));

        service.streamChat(request, new AiStreamHandler() {});

        assertEquals(List.of("secondary"), service.getLastAttemptedProviders());
    }

    /**
     * 深度思考模式下应优先尝试支持 thinking 的候选。
     */
    @Test
    void streamChatUsesThinkingCapableCandidatesWhenThinkingEnabled() {
        RecordingProvider nonThinkingProvider = RecordingProvider.success("stub", "stub-chat", List.of("stub"));
        RecordingProvider thinkingProvider = RecordingProvider.success("deepseek", "deepseek-thinking", List.of("thinking"));
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(nonThinkingProvider, thinkingProvider),
            new AiProviderHealthRegistry(2, 30_000L),
            minimalSelectorWithCandidates(minimalPropertiesWithCandidates(
                candidate("stub-chat", "stub", "stub-chat", 1, false),
                candidate("deepseek-thinking", "deepseek", "deepseek-thinking", 2, true)
            ))
        );

        List<String> deltas = new ArrayList<>();
        service.streamChat(AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .thinkingEnabled(true)
            .stream(true)
            .build(), new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }
        });

        assertEquals(List.of("deepseek"), service.getLastAttemptedProviders());
        assertEquals(List.of("thinking"), deltas);
    }

    /**
     * 普通请求即使遇到 provider 误发 thinking 增量，调度层也不能向下游泄漏深度思考。
     */
    @Test
    void streamChatSuppressesThinkingDeltaWhenThinkingDisabled() {
        AiProviderClient noisyProvider = new AiProviderClient() {
            @Override
            public String provider() {
                return "deepseek";
            }

            @Override
            public AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler) {
                handler.onThinkingDelta("不应透出的思考");
                handler.onContentDelta("普通回答");
                handler.onComplete();
                return new AiStreamSession(() -> {}, CompletableFuture.completedFuture(null));
            }
        };
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(noisyProvider),
            new AiProviderHealthRegistry(2, 30_000L),
            minimalSelectorWithCandidates(minimalPropertiesWithCandidates(
                candidate("deepseek-thinking", "deepseek", "deepseek-thinking", 1, true)
            ))
        );
        List<String> thinkingDeltas = new ArrayList<>();
        List<String> contentDeltas = new ArrayList<>();

        service.streamChat(AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .thinkingEnabled(false)
            .stream(true)
            .build(), new AiStreamHandler() {
            @Override
            public void onThinkingDelta(String delta) {
                thinkingDeltas.add(delta);
            }

            @Override
            public void onContentDelta(String delta) {
                contentDeltas.add(delta);
            }
        });

        assertEquals(List.of(), thinkingDeltas);
        assertEquals(List.of("普通回答"), contentDeltas);
    }

    /**
     * 普通请求下被丢弃的 thinking 不能算首包成功，否则只返回 reasoning 的候选会截断正常 fallback。
     */
    @Test
    void streamChatFallsBackWhenOnlySuppressedThinkingArrives() {
        AiProviderClient thinkingOnlyProvider = new AiProviderClient() {
            @Override
            public String provider() {
                return "primary";
            }

            @Override
            public AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler) {
                handler.onThinkingDelta("不应透出的思考");
                handler.onComplete();
                return new AiStreamSession(() -> {}, CompletableFuture.completedFuture(null));
            }
        };
        RecordingProvider fallbackProvider = RecordingProvider.success("secondary", "qwen-plus", List.of("fallback"));
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(thinkingOnlyProvider, fallbackProvider),
            new AiProviderHealthRegistry(2, 30_000L),
            minimalSelectorWithCandidates(minimalPropertiesWithCandidates(
                candidate("primary-chat", "primary", "primary-chat", 1, false),
                candidate("qwen-plus", "secondary", "qwen-plus", 2, false)
            ))
        );
        List<String> contentDeltas = new ArrayList<>();

        service.streamChat(AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .thinkingEnabled(false)
            .stream(true)
            .build(), new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                contentDeltas.add(delta);
            }
        });

        assertEquals(List.of("primary", "secondary"), service.getLastAttemptedProviders());
        assertEquals(List.of("fallback"), contentDeltas);
    }

    /**
     * OpenAI 兼容客户端只是内部协议适配器，对外 metadata 必须保留候选池中的真实模型商。
     */
    @Test
    void streamChatReportsCandidateProviderWhenCompatibleClientHandlesVendor() {
        AiProviderClient compatibleProvider = new AiProviderClient() {
            @Override
            public String provider() {
                return "openai-compatible";
            }

            @Override
            public AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler) {
                handler.onContentDelta("兼容客户端回答");
                handler.onComplete();
                return new AiStreamSession(() -> {}, CompletableFuture.completedFuture(null));
            }
        };
        AiProperties properties = minimalPropertiesWithCandidates(
            candidate("qwen-plus", "bailian", "qwen-plus-latest", 1, false)
        );
        properties.getProviders().put("bailian", provider("http://127.0.0.1:5", "key"));
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(compatibleProvider),
            new AiProviderHealthRegistry(2, 30_000L),
            new AiModelSelector(properties)
        );
        List<String> terminals = new ArrayList<>();

        service.streamChat(AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .stream(true)
            .build(), new AiStreamHandler() {
            @Override
            public void onMetadata(String provider, String model) {
                terminals.add(provider + ":" + model);
            }
        });

        assertEquals(List.of("bailian"), service.getLastAttemptedProviders());
        assertEquals(List.of("bailian:qwen-plus-latest"), terminals);
    }

    /**
     * 候选 provider 没有注册客户端时，应跳过该候选并继续尝试后续模型。
     */
    @Test
    void streamChatSkipsCandidateWhenProviderClientIsMissing() {
        RecordingProvider healthyProvider = RecordingProvider.success("secondary", "qwen-plus", List.of("fallback"));
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(healthyProvider),
            new AiProviderHealthRegistry(2, 30_000L),
            new AiModelSelector(minimalProperties())
        );
        List<String> deltas = new ArrayList<>();

        service.streamChat(AiConversationRequest.builder()
            .messages(List.of(ChatMessage.userMessage(1L, "你好")))
            .stream(true)
            .build(), new AiStreamHandler() {
            @Override
            public void onContentDelta(String delta) {
                deltas.add(delta);
            }
        });

        assertEquals(List.of("secondary"), service.getLastAttemptedProviders());
        assertEquals(List.of("fallback"), deltas);
    }

    /**
     * 调度服务是单例 Bean，单次请求的尝试记录必须隔离，不能被并发请求交叉清空或拼接。
     */
    @Test
    void streamChatKeepsAttemptedProviderSnapshotIsolatedAcrossConcurrentRequests() throws Exception {
        CountDownLatch primaryStarted = new CountDownLatch(2);
        CountDownLatch releaseProviders = new CountDownLatch(1);
        RecordingProvider slowPrimary = RecordingProvider.blockingSuccess(
            "primary",
            "deepseek-chat",
            List.of("primary"),
            primaryStarted,
            releaseProviders
        );
        RecordingProvider secondary = RecordingProvider.success("secondary", "qwen-plus", List.of("secondary"));
        AiModelDispatchService service = new AiModelDispatchService(
            List.of(slowPrimary, secondary),
            new AiProviderHealthRegistry(2, 30_000L),
            new AiModelSelector(minimalProperties())
        );

        CompletableFuture<List<String>> first = CompletableFuture.supplyAsync(() -> {
            service.streamChat(AiConversationRequest.builder()
                .messages(List.of(ChatMessage.userMessage(1L, "一号请求")))
                .stream(true)
                .build(), new AiStreamHandler() {});
            return service.getLastAttemptedProviders();
        });
        CompletableFuture<List<String>> second = CompletableFuture.supplyAsync(() -> {
            service.streamChat(AiConversationRequest.builder()
                .messages(List.of(ChatMessage.userMessage(2L, "二号请求")))
                .stream(true)
                .build(), new AiStreamHandler() {});
            return service.getLastAttemptedProviders();
        });

        assertTrue(primaryStarted.await(1, TimeUnit.SECONDS));
        releaseProviders.countDown();

        assertEquals(List.of("primary"), first.get(1, TimeUnit.SECONDS));
        assertEquals(List.of("primary"), second.get(1, TimeUnit.SECONDS));
        assertEquals(List.of("primary"), service.getLastAttemptedProviders());
    }

    /**
     * 用于验证路由行为的内存 provider。
     */
    private record RecordingProvider(
        String providerName,
        String modelName,
        Mode mode,
        List<String> deltas,
        CountDownLatch started,
        CountDownLatch release
    ) implements AiProviderClient {

        private static RecordingProvider failing(String providerName, String modelName) {
            return new RecordingProvider(providerName, modelName, Mode.FAIL, List.of(), null, null);
        }

        private static RecordingProvider probeFailure(String providerName, String modelName) {
            return new RecordingProvider(providerName, modelName, Mode.PROBE_FAIL, List.of(), null, null);
        }

        private static RecordingProvider success(String providerName, String modelName, List<String> deltas) {
            return new RecordingProvider(providerName, modelName, Mode.SUCCESS, deltas, null, null);
        }

        private static RecordingProvider blockingSuccess(
            String providerName,
            String modelName,
            List<String> deltas,
            CountDownLatch started,
            CountDownLatch release
        ) {
            return new RecordingProvider(providerName, modelName, Mode.BLOCKING_SUCCESS, deltas, started, release);
        }

        @Override
        public String provider() {
            return providerName;
        }

        @Override
        public AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler) {
            if (mode == Mode.FAIL) {
                throw new IllegalStateException(providerName + " unavailable");
            }
            CompletableFuture<Void> future = new CompletableFuture<>();
            if (mode == Mode.PROBE_FAIL) {
                handler.onThinkingDelta("dirty-thinking");
                handler.onError(new IllegalStateException(providerName + " probe failed"));
                future.completeExceptionally(new IllegalStateException(providerName + " probe failed"));
                return new AiStreamSession(() -> {}, future);
            }
            if (mode == Mode.BLOCKING_SUCCESS) {
                handler.onContentDelta(deltas.getFirst());
                if (started != null) {
                    started.countDown();
                }
                try {
                    if (release != null) {
                        release.await(1, TimeUnit.SECONDS);
                    }
                    handler.onComplete();
                    future.complete(null);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(exception);
                }
                return new AiStreamSession(() -> future.cancel(true), future);
            }
            for (String delta : deltas) {
                handler.onContentDelta(delta);
            }
            handler.onComplete();
            future.complete(null);
            return new AiStreamSession(() -> {}, future);
        }
    }

    /**
     * 使用最小新配置构造默认模型选择器，避免测试依赖完整 Spring 环境。
     */
    private AiModelSelector minimalSelectorWithCandidates(AiProperties properties) {
        return new AiModelSelector(properties);
    }

    /**
     * 创建默认测试配置，其中包含 deepseek 与 stub 两个候选。
     * @return 测试专用 AI 配置。
     */
    private AiProperties minimalProperties() {
        return minimalPropertiesWithCandidates(
            candidate("deepseek-chat", "primary", "deepseek-chat", 1, false),
            candidate("qwen-plus", "secondary", "qwen-plus", 2, true)
        );
    }

    /**
     * 使用指定候选组装测试配置，便于聚焦路由排序行为。
     * @param candidates 候选模型数组。
     * @return 测试专用 AI 配置。
     */
    private AiProperties minimalPropertiesWithCandidates(AiProperties.ChatCandidate... candidates) {
        AiProperties properties = new AiProperties();
        properties.setSelection(new AiProperties.Selection());
        properties.getSelection().setFailureThreshold(2);
        properties.getSelection().setOpenDurationMs(30_000L);
        properties.getSelection().setFirstPacketTimeoutMs(200L);
        properties.setProviders(new java.util.HashMap<>());
        properties.getProviders().put("primary", provider("http://127.0.0.1:1", "key"));
        properties.getProviders().put("secondary", provider("http://127.0.0.1:2", "key"));
        properties.getProviders().put("deepseek", provider("http://127.0.0.1:3", "key"));
        properties.getProviders().put("stub", provider("http://127.0.0.1:4", ""));
        AiProperties.ChatModelGroup group = new AiProperties.ChatModelGroup();
        group.setDefaultModel(candidates[0].getId());
        group.setDeepThinkingModel(candidates[candidates.length - 1].getId());
        List<AiProperties.ChatCandidate> sortedCandidates = new ArrayList<>(List.of(candidates));
        sortedCandidates.sort(Comparator.comparing(AiProperties.ChatCandidate::getPriority));
        group.setCandidates(sortedCandidates);
        properties.setChat(group);
        return properties;
    }

    /**
     * 生成单个测试候选，避免每个测试手写重复样板。
     * @param id 候选 ID。
     * @param provider provider 名称。
     * @param model 模型名称。
     * @param priority 优先级。
     * @param supportsThinking 是否支持 thinking。
     * @return 测试候选。
     */
    private AiProperties.ChatCandidate candidate(String id, String provider, String model, int priority, boolean supportsThinking) {
        AiProperties.ChatCandidate candidate = new AiProperties.ChatCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel(model);
        candidate.setPriority(priority);
        candidate.setSupportsThinking(supportsThinking);
        candidate.setSupportsVision(false);
        candidate.setEnabled(true);
        return candidate;
    }

    /**
     * 生成最小 provider 配置，满足选择器构建 `AiModelTarget` 的依赖。
     * @param url 基础 URL。
     * @param apiKey API Key。
     * @return provider 配置。
     */
    private AiProperties.Provider provider(String url, String apiKey) {
        AiProperties.Provider provider = new AiProperties.Provider();
        provider.setBaseUrl(url);
        provider.setApiKey(apiKey);
        return provider;
    }

    /**
     * 模拟 provider 行为的三种模式。
     */
    private enum Mode {
        SUCCESS,
        FAIL,
        PROBE_FAIL,
        BLOCKING_SUCCESS
    }
}
