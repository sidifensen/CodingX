package com.codingx.chat.interfaces.response;

/**
 * 定义首页示例问题接口返回的数据载体。
 * @param id 示例问题标识。
 * @param questionText 示例问题内容。
 * @param category 分类标签。
 */
public record ChatSampleQuestionResponse(
    Long id,
    String questionText,
    String category
) {
}
