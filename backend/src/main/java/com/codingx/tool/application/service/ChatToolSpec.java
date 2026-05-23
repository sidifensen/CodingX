package com.codingx.tool.application.service;

import java.util.Map;

/**
 * 描述一个可暴露给模型自主调用的本地工具。
 * 只有真实注册且启用的 Java 执行器才能生成该结构，避免模型看到数据库里的占位工具。
 *
 * @param name 工具编码。
 * @param description 工具说明。
 * @param parameters OpenAI function tool JSON Schema 参数定义。
 */
public record ChatToolSpec(
    String name,
    String description,
    Map<String, Object> parameters
) {
}
