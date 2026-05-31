package com.codingx.tool.application.service;

import java.util.Map;

/**
 * 描述一个可暴露给模型自主调用的本地工具。
 * 只有真实注册且启用的 Java 执行器才能生成该结构，避免模型看到数据库里的占位工具。
 *
 * @param name 暴露给模型的工具编码，必须能匹配已注册执行器。
 * @param description 工具能力说明，模型据此判断是否需要调用该工具。
 * @param parameters OpenAI function tool JSON Schema 参数定义，不需要参数时可为空对象。
 */
public record ChatToolSpec(
    String name, // 暴露给模型的工具编码，必须能匹配已注册执行器。
    String description, // 工具能力说明，模型据此判断是否需要调用该工具。
    Map<String, Object> parameters // OpenAI function tool JSON Schema 参数定义，不需要参数时可为空对象。
) {
}
