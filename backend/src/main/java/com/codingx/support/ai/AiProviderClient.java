package com.codingx.support.ai;

/**
 * 定义单个模型 provider 需要实现的最小流式调用契约。
 */
public interface AiProviderClient {

    /**
     * 判断当前 provider 是否支持处理该请求。
     * @param request 统一请求对象。
     * @return 是否支持。
     */
    boolean supports(AiConversationRequest request);

    /**
     * 返回 provider 的候选元信息。
     * @return 候选描述。
     */
    AiProviderCandidate candidate();

    /**
     * 以流式方式执行对话请求。
     * @param request 统一请求对象。
     * @param handler 流式回调。
     */
    void streamChat(AiConversationRequest request, AiStreamHandler handler);
}
