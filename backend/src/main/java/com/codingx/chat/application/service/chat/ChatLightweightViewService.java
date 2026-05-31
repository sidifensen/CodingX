package com.codingx.chat.application.service;

import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatSampleQuestion;
import com.codingx.chat.interfaces.response.ChatAttachmentResponse;
import com.codingx.chat.interfaces.response.ChatAttachmentUploadCapabilitiesResponse;
import com.codingx.chat.interfaces.response.ChatSampleQuestionResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户侧聊天轻量视图服务，负责示例问题、附件元数据和上传能力响应投影。
 */
@Service
@RequiredArgsConstructor
public class ChatLightweightViewService {

    /**
     * 单条消息最多允许携带的附件数量，前端上传控件和后端消息发送约束保持一致。
     */
    private static final long MAX_FILE_COUNT = 9L;

    /**
     * 运行时设置服务，用于读取附件上传大小限制等动态配置。
     */
    private final RuntimeSettingService runtimeSettingService;

    /**
     * 将启用的首页示例问题转换为接口响应。
     * @param questions 示例问题领域对象列表。
     * @return 示例问题响应列表。
     */
    public List<ChatSampleQuestionResponse> toSampleQuestionResponses(List<ChatSampleQuestion> questions) {
        // 步骤 1：示例问题服务已经完成启用状态筛选，视图层保持原顺序逐项投影。
        // 步骤 2：只暴露前端欢迎区需要的 id、问题文案和分类标签，避免泄露排序等内部字段。
        return questions.stream()
            .map(question -> new ChatSampleQuestionResponse(
                question.getId(),
                question.getQuestionText(),
                question.getCategory()
            ))
            .toList();
    }

    /**
     * 将附件领域对象转换为上传接口响应。
     * @param attachment 附件领域对象。
     * @return 附件响应对象。
     */
    public ChatAttachmentResponse toAttachmentResponse(ChatAttachment attachment) {
        // 步骤 1：保留附件归属、类型、文件元信息和预览地址，供前端上传后立即渲染附件条目。
        // 步骤 2：contentSummary 可能为空，前端只在文本/PDF 提取成功时展示摘要。
        return new ChatAttachmentResponse(
            attachment.getId(),
            attachment.getConversationId(),
            attachment.getMessageId(),
            attachment.getAttachmentType(),
            attachment.getFileName(),
            attachment.getFileExt(),
            attachment.getMimeType(),
            attachment.getFileSize(),
            attachment.getPreviewUrl(),
            attachment.getContentSummary(),
            attachment.getStatus(),
            attachment.getCreatedAt()
        );
    }

    /**
     * 生成附件上传能力响应。
     * @return 上传能力响应。
     */
    public ChatAttachmentUploadCapabilitiesResponse toUploadCapabilitiesResponse() {
        // 步骤 1：读取运行时附件大小限制，保证前端提示和后端校验使用同一配置来源。
        long maxFileSizeBytes = runtimeSettingService.chatAttachmentMaxFileSizeBytes();
        // 步骤 2：返回单条消息附件数量上限，前端据此限制选择数量。
        return new ChatAttachmentUploadCapabilitiesResponse(maxFileSizeBytes, MAX_FILE_COUNT);
    }
}
