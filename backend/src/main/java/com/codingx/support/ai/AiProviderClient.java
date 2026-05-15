package com.codingx.support.ai;

/**
 * 定义单个 provider 客户端的最小调用契约，路由层按模型目标驱动具体实现。
 */
public interface AiProviderClient {

    /**
     * 返回当前客户端负责的 provider 名称。
     * @return provider 名称。
     */
    String provider();

    /**
     * 针对指定模型目标发起流式对话调用。
     * @param request 统一请求对象。
     * @param target 目标模型配置。
     * @param handler 流式事件处理器。
     * @return 可取消的流式会话。
     */
    AiStreamSession streamChat(AiConversationRequest request, AiModelTarget target, AiStreamHandler handler);
}
