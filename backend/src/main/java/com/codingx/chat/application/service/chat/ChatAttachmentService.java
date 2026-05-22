package com.codingx.chat.application.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.repository.ChatAttachmentRepository;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.BusinessException;
import com.codingx.common.exception.ForbiddenException;
import com.codingx.common.exception.NotFoundException;
import com.codingx.common.storage.RustFsChatAttachmentClient;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 提供聊天附件上传、读取与消息绑定校验能力。
 */
@Service
@RequiredArgsConstructor
public class ChatAttachmentService {

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final int ATTACHMENT_TEXT_SUMMARY_MAX_LENGTH = 1600;
    private static final Set<String> TEXT_SUMMARY_EXTENSIONS = Set.of(
        "txt", "md", "rtf", "html", "xml",
        "csv", "tsv", "json", "yaml", "yml", "sql",
        "java", "py", "js", "ts", "c", "cpp", "go", "rs", "sh", "bat", "ps1",
        "log"
    );
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
            throw new BusinessException("CHAT_ATTACHMENT_READ_FAILED", ErrorMessageCatalog.CHAT_ATTACHMENT_READ_FAILED);
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
            .contentSummary(extractAttachmentTextSummary(fileExt, bytes))
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
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_ATTACHMENT_NOT_FOUND));
        if (!userId.equals(attachment.getUploadedBy())) {
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_ATTACHMENT_FORBIDDEN_ACCESS);
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
            throw new NotFoundException(ErrorMessageCatalog.CHAT_ATTACHMENT_PARTIAL_NOT_FOUND);
        }
        Set<Long> requestedIdSet = new HashSet<>(normalizedIds);
        for (ChatAttachment attachment : attachments) {
            if (!requestedIdSet.contains(attachment.getId())) {
                throw new NotFoundException(ErrorMessageCatalog.CHAT_ATTACHMENT_PARTIAL_NOT_FOUND);
            }
            if (!userId.equals(attachment.getUploadedBy())) {
                throw new ForbiddenException(ErrorMessageCatalog.CHAT_ATTACHMENT_FORBIDDEN_USE);
            }
            if (attachment.getMessageId() != null) {
                throw new BusinessException("CHAT_ATTACHMENT_ALREADY_USED", ErrorMessageCatalog.CHAT_ATTACHMENT_ALREADY_USED);
            }
            if (attachment.getConversationId() != null && !attachment.getConversationId().equals(conversationId)) {
                throw new BusinessException("CHAT_ATTACHMENT_CONVERSATION_MISMATCH", ErrorMessageCatalog.CHAT_ATTACHMENT_CONVERSATION_MISMATCH);
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
            throw new BusinessException("CHAT_ATTACHMENT_DOWNLOAD_FAILED", ErrorMessageCatalog.CHAT_ATTACHMENT_DOWNLOAD_FAILED);
        }
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CHAT_ATTACHMENT_EMPTY", ErrorMessageCatalog.CHAT_ATTACHMENT_EMPTY);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("CHAT_ATTACHMENT_TOO_LARGE", ErrorMessageCatalog.CHAT_ATTACHMENT_TOO_LARGE);
        }
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "attachment.bin");
        String fileExt = StrUtil.blankToDefault(FileUtil.extName(fileName), "").toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(fileExt) || !ALLOWED_EXTENSIONS.contains(fileExt)) {
            throw new BusinessException("CHAT_ATTACHMENT_TYPE_NOT_ALLOWED", ErrorMessageCatalog.CHAT_ATTACHMENT_TYPE_NOT_ALLOWED);
        }
        if (isAudioOrVideo(fileExt)) {
            throw new BusinessException("CHAT_ATTACHMENT_TYPE_NOT_ALLOWED", ErrorMessageCatalog.CHAT_ATTACHMENT_AUDIO_VIDEO_NOT_SUPPORTED);
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
            throw new ForbiddenException(ErrorMessageCatalog.CHAT_CONVERSATION_FORBIDDEN);
        }
    }

    /**
     * 提取可直接注入模型提示的文本摘要，避免纯文档附件在非视觉模型场景下完全不可见。
     * @param fileExt 文件扩展名。
     * @param bytes 文件字节。
     * @return 限长后的文本摘要。
     */
    private String extractAttachmentTextSummary(String fileExt, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String rawText = null;
        if (TEXT_SUMMARY_EXTENSIONS.contains(fileExt)) {
            rawText = StrUtil.trimToEmpty(new String(bytes, CharsetUtil.CHARSET_UTF_8));
        } else if ("pdf".equalsIgnoreCase(fileExt)) {
            rawText = extractPdfText(bytes);
        }
        if (StrUtil.isBlank(rawText)) {
            return null;
        }
        return normalizeSummaryText(rawText);
    }

    /**
     * 使用 PDF 解析器抽取正文，保证简历等 PDF 文件可进入模型上下文。
     * @param bytes PDF 文件字节。
     * @return 抽取结果，失败时返回空串。
     */
    private String extractPdfText(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper pdfTextStripper = new PDFTextStripper();
            return StrUtil.trimToEmpty(pdfTextStripper.getText(document));
        } catch (Exception exception) {
            return "";
        }
    }

    /**
     * 统一归一化摘要文本，清理空白符并执行限长，避免污染模型输入上下文。
     * @param rawText 原始文本。
     * @return 归一化摘要。
     */
    private String normalizeSummaryText(String rawText) {
        if (StrUtil.isBlank(rawText)) {
            return null;
        }
        String normalizedText = rawText.replace("\u0000", "").replaceAll("\\s+", " ").trim();
        if (StrUtil.isBlank(normalizedText)) {
            return null;
        }
        return StrUtil.maxLength(normalizedText, ATTACHMENT_TEXT_SUMMARY_MAX_LENGTH);
    }
}

