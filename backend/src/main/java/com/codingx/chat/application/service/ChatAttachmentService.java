package com.codingx.chat.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.repository.ChatAttachmentRepository;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.storage.RustFsChatAttachmentClient;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 提供聊天附件上传、读取与消息绑定校验能力。
 */
@Service
@RequiredArgsConstructor
public class ChatAttachmentService {

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "txt", "md", "rtf", "html", "xml",
        "doc", "docx", "ppt", "pptx", "xls", "xlsx", "odt", "odp", "ods",
        "csv", "tsv", "json", "yaml", "yml", "sql",
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff", "svg", "heic",
        "java", "py", "js", "ts", "c", "cpp", "go", "rs", "sh", "bat", "ps1",
        "zip", "7z", "rar", "tar", "gz", "tgz",
        "epub", "eml", "msg", "log"
    );

    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final RustFsChatAttachmentClient rustFsChatAttachmentClient;

    /**
     * 上传聊天附件并持久化元数据。
     * @param file 上传文件。
     * @param conversationId 会话主键，可为空。
     * @return 附件记录。
     */
    public ChatAttachment upload(MultipartFile file, Long conversationId) {
        Long userId = StpUtil.getLoginIdAsLong();
        validateUploadFile(file);
        if (conversationId != null) {
            assertConversationOwner(conversationId, userId);
        }
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "attachment.bin");
        String fileExt = StrUtil.blankToDefault(FileUtil.extName(fileName), "").toLowerCase(Locale.ROOT);
        String mimeType = StrUtil.blankToDefault(file.getContentType(), "application/octet-stream");
        String attachmentType = mimeType.startsWith("image/") ? "image" : "file";
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception exception) {
            throw new BusinessException("CHAT_ATTACHMENT_READ_FAILED", "上传文件读取失败");
        }
        String storageKey = rustFsChatAttachmentClient.upload(bytes, fileName, mimeType);
        LocalDateTime now = LocalDateTime.now();
        ChatAttachment attachment = ChatAttachment.builder()
            .id(IdUtil.getSnowflakeNextId())
            .conversationId(conversationId)
            .uploadedBy(userId)
            .attachmentType(attachmentType)
            .fileName(fileName)
            .fileExt(fileExt)
            .mimeType(mimeType)
            .fileSize(file.getSize())
            .storageKey(storageKey)
            .previewUrl("/api/chat/attachments/" + IdUtil.fastSimpleUUID())
            .status("UPLOADED")
            .createdAt(now)
            .updatedAt(now)
            .deleted(0)
            .build();
        // 步骤：预览 URL 采用可稳定推导的接口路径，避免前端依赖对象存储私有地址。
        attachment = attachment.toBuilder()
            .previewUrl("/api/chat/attachments/" + attachment.getId() + "/content")
            .build();
        chatAttachmentRepository.save(attachment);
        return attachment;
    }

    /**
     * 查询附件并校验当前登录用户权限。
     * @param attachmentId 附件主键。
     * @return 附件记录。
     */
    public ChatAttachment requireOwnedAttachment(Long attachmentId) {
        Long userId = StpUtil.getLoginIdAsLong();
        ChatAttachment attachment = chatAttachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new NotFoundException("附件不存在"));
        if (!userId.equals(attachment.getUploadedBy())) {
            throw new ForbiddenException("你无权访问该附件");
        }
        return attachment;
    }

    /**
     * 按主键列表查询并校验附件归属，供发送消息时绑定附件。
     * @param attachmentIds 附件主键集合。
     * @param conversationId 会话主键。
     * @param userId 用户主键。
     * @return 已校验附件列表。
     */
    public List<ChatAttachment> requireOwnedAttachments(List<Long> attachmentIds, Long conversationId, Long userId) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = attachmentIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<ChatAttachment> attachments = chatAttachmentRepository.findByIds(normalizedIds);
        if (attachments.size() != normalizedIds.size()) {
            throw new NotFoundException("部分附件不存在");
        }
        Set<Long> requestedIdSet = new HashSet<>(normalizedIds);
        for (ChatAttachment attachment : attachments) {
            if (!requestedIdSet.contains(attachment.getId())) {
                throw new NotFoundException("部分附件不存在");
            }
            if (!userId.equals(attachment.getUploadedBy())) {
                throw new ForbiddenException("你无权使用该附件");
            }
            if (attachment.getMessageId() != null) {
                throw new BusinessException("CHAT_ATTACHMENT_ALREADY_USED", "附件已被其他消息使用");
            }
            if (attachment.getConversationId() != null && !attachment.getConversationId().equals(conversationId)) {
                throw new BusinessException("CHAT_ATTACHMENT_CONVERSATION_MISMATCH", "附件不属于当前会话");
            }
        }
        return attachments;
    }

    /**
     * 将附件绑定到指定消息和运行上下文。
     * @param attachment 附件记录。
     * @param conversationId 会话主键。
     * @param messageId 消息主键。
     * @param runId 执行主链路主键。
     */
    public void bindToMessage(ChatAttachment attachment, Long conversationId, Long messageId, Long runId) {
        LocalDateTime now = LocalDateTime.now();
        ChatAttachment persisted = attachment.toBuilder()
            .conversationId(conversationId)
            .messageId(messageId)
            .runId(runId)
            .updatedAt(now)
            .build();
        chatAttachmentRepository.save(persisted);
    }

    /**
     * 查询消息附件列表。
     * @param messageId 消息主键。
     * @return 附件列表。
     */
    public List<ChatAttachment> listByMessageId(Long messageId) {
        return chatAttachmentRepository.findByMessageId(messageId);
    }

    /**
     * 下载附件内容。
     * @param attachment 附件记录。
     * @return 文件字节。
     */
    public byte[] downloadContent(ChatAttachment attachment) {
        try {
            return rustFsChatAttachmentClient.download(attachment.getStorageKey());
        } catch (Exception exception) {
            throw new BusinessException("CHAT_ATTACHMENT_DOWNLOAD_FAILED", "附件下载失败");
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CHAT_ATTACHMENT_EMPTY", "请先选择上传文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("CHAT_ATTACHMENT_TOO_LARGE", "上传文件大小不能超过 20MB");
        }
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "attachment.bin");
        String fileExt = StrUtil.blankToDefault(FileUtil.extName(fileName), "").toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(fileExt) || !ALLOWED_EXTENSIONS.contains(fileExt)) {
            throw new BusinessException("CHAT_ATTACHMENT_TYPE_NOT_ALLOWED", "当前文件格式暂不支持解析");
        }
        if (isAudioOrVideo(fileExt)) {
            throw new BusinessException("CHAT_ATTACHMENT_TYPE_NOT_ALLOWED", "暂不支持音频和视频文件");
        }
    }

    private boolean isAudioOrVideo(String fileExt) {
        return Set.of(
            "mp3", "wav", "m4a", "aac", "flac", "ogg",
            "mp4", "mov", "avi", "mkv", "webm", "m4v"
        ).contains(fileExt);
    }

    private void assertConversationOwner(Long conversationId, Long userId) {
        ChatConversation conversation = chatConversationRepository.requireById(conversationId);
        if (!userId.equals(conversation.getCreatedBy())) {
            throw new ForbiddenException("你无权访问该会话");
        }
    }
}
