package com.codingx.cli.backend;

/**
 * 单个 SSE 事件块，包含事件名和原始 data 文本。
 *
 * @param eventName SSE event 字段，缺失时默认为 message。
 * @param data SSE data 字段拼接后的原始文本。
 */
record SseEvent(String eventName, String data) {
}
