package com.codingx.chat.interfaces.response;

import com.codingx.chat.domain.model.ChatMessageRole;
import com.codingx.chat.domain.model.ChatMessageStatus;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天消息列表接口的响应载体。
 *
 * @param id 消息主键，序列化为字符串避免前端 Long 精度丢失。
 * @param conversationId 消息所属会话标识。
 * @param role 消息角色，区分用户输入与助手输出。
 * @param content 消息正文；用户消息保留原始输入，助手消息保留模型最终回答。
 * @param thinkingContent 模型深度思考内容，未开启或供应商未返回时为空。
 * @param thinkingDuration 深度思考耗时，单位秒，未统计时为空。
 * @param status 消息生成状态，决定前端是否显示错误或加载态。
 * @param provider 生成助手消息的模型供应商，用户消息可为空。
 * @param model 生成助手消息的模型标识，用户消息可为空。
 * @param errorMessage 消息生成失败时返回给前端的中文错误文案。
 * @param createdAt 消息创建时间，用于历史消息排序和展示。
 * @param attachments 消息关联附件列表，未绑定附件时为空列表。
 * @param skillCodes 从用户消息正文解析出的技能编码，用于历史回放展示。
 * @param userVote 当前用户对该消息的投票值（1 点赞，-1 点踩，null 表示未投票）。
 */
public record ChatMessageResponse(
    Long id, // 消息主键，序列化为字符串避免前端 Long 精度丢失。
    Long conversationId, // 消息所属会话标识。
    ChatMessageRole role, // 消息角色，区分用户输入与助手输出。
    String content, // 消息正文；用户消息保留原始输入，助手消息保留模型最终回答。
    String thinkingContent, // 模型深度思考内容，未开启或供应商未返回时为空。
    Integer thinkingDuration, // 深度思考耗时，单位秒，未统计时为空。
    ChatMessageStatus status, // 消息生成状态，决定前端是否显示错误或加载态。
    String provider, // 生成助手消息的模型供应商，用户消息可为空。
    String model, // 生成助手消息的模型标识，用户消息可为空。
    String errorMessage, // 消息生成失败时返回给前端的中文错误文案。
    LocalDateTime createdAt, // 消息创建时间，用于历史消息排序和展示。
    List<ChatAttachmentResponse> attachments, // 消息关联附件列表，未绑定附件时为空列表。
    List<String> skillCodes, // 从用户消息正文解析出的技能编码，用于历史回放展示。
    Integer userVote // 当前用户对该消息的投票值（1 点赞，-1 点踩，null 表示未投票）。
) {
}
