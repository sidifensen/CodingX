package com.codingx.mcp.application.service;

import java.util.Map;

/**
 * 定义 MCP 工具执行过程中的进度回调契约。
 * <p>
 * 业务意图：
 * 1. 让工具在真实步骤发生时上报进度，而不是前端伪造进度动画
 * 2. 回调参数采用阶段 + 文案 + 详情三元组，便于前后端统一扩展
 */
@FunctionalInterface
public interface ChatMcpProgressListener {

    /**
     * 上报一次执行进度。
     * @param stage 阶段标识，建议使用短英文标识（如 resolve-coordinates）。
     * @param message 阶段文案，直接用于前端展示。
     * @param detail 阶段详情，记录关键上下文（可为空）。
     */
    void onProgress(String stage, String message, Map<String, Object> detail);

    /**
     * 返回空实现监听器，避免调用方重复判空。
     * @return 空监听器。
     */
    static ChatMcpProgressListener noop() {
        return (stage, message, detail) -> {
        };
    }
}
