package com.codingx.tool.application.service;

import java.util.Map;

/**
 * 可暴露给模型自主调用的本地工具规格，统一描述工具编码、说明和函数参数 Schema。
 * 只有真实注册且启用的 Java 执行器才能生成该结构，避免模型看到数据库里的占位工具。
 *
 * @param name 暴露给模型的工具编码，必须能匹配已注册执行器。
 * @param description 工具能力说明，模型据此判断是否需要调用该工具。
 * @param parameters OpenAI function tool JSON Schema 参数定义，不需要参数时可为空对象。
 * @param canonicalToolCode 后端真实执行器编码；别名工具会使用该字段回落到短工具编码。
 */
public record ChatToolSpec(
    String name, // 暴露给模型的工具编码，必须能匹配已注册执行器。
    String description, // 工具能力说明，模型据此判断是否需要调用该工具。
    Map<String, Object> parameters, // OpenAI function tool JSON Schema 参数定义，不需要参数时可为空对象。
    String canonicalToolCode // 后端真实执行器编码；别名工具会使用该字段回落到短工具编码。
) {

    /**
     * 兼容旧调用方，未显式传 canonicalToolCode 时默认与模型可见名称相同。
     * @param name 暴露给模型的工具编码。
     * @param description 工具能力说明。
     * @param parameters OpenAI function tool JSON Schema 参数定义。
     */
    public ChatToolSpec(String name, String description, Map<String, Object> parameters) {
        this(name, description, parameters, name);
    }
}
