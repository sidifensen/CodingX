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

    /**
     * 可注入模型上下文的附件摘要最大长度，避免大文件正文污染提示词。
     */
    private static final int ATTACHMENT_TEXT_SUMMARY_MAX_LENGTH = 1600;

    /**
     * 允许直接按 UTF-8 抽取文本摘要的扩展名集合。
     */
    private static final Set<String> TEXT_SUMMARY_EXTENSIONS = Set.of(
        "txt", "md", "rtf", "html", "xml",
        "csv", "tsv", "json", "yaml", "yml", "sql",
        "java", "py", "js", "ts", "c", "cpp", "go", "rs", "sh", "bat", "ps1",
        "log"
    );

    /**
     * 聊天附件允许上传的扩展名白名单，音视频会在额外分支中显式拒绝。
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "txt", "md", "rtf", "html", "xml",
        "doc", "docx", "ppt", "pptx", "xls", "xlsx", "odt", "odp", "ods",
        "csv", "tsv", "json", "yaml", "yml", "sql",
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff", "svg", "heic",
        "java", "py", "js", "ts", "c", "cpp", "go", "rs", "sh", "bat", "ps1",
        "zip", "7z", "rar", "tar", "gz", "tgz",
        "epub", "eml", "msg", "log"
    );

    /**
     * 附件仓储，用于持久化上传元数据和查询消息绑定附件。
     */
    private final ChatAttachmentRepository chatAttachmentRepository;

    /**
     * 会话仓储，用于上传时校验附件绑定会话是否属于当前用户。
     */
    private final ChatConversationRepository chatConversationRepository;

    /**
     * 附件对象存储客户端，用于上传和下载附件二进制内容。
     */
    private final RustFsChatAttachmentClient rustFsChatAttachmentClient;

    /**
     * 运行时配置服务，用于读取附件大小限制。
     */
    private final RuntimeSettingService runtimeSettingService;

    /**
     * 上传聊天附件并持久化元数据。
     * @param file 上传文件。
     * @param conversationId 会话主键，可为空。
     * @return 附件记录。
     */
    public ChatAttachment upload(MultipartFile file, Long conversationId) {
        // 步骤 1：获取当前登录用户并执行空文件、大小、扩展名和音视频类型校验。
        Long userId = StpUtil.getLoginIdAsLong();
        validateUploadFile(file);
        // 步骤 2：指定会话时校验会话归属，避免把附件挂到其他用户会话。
        if (conversationId != null) {
            assertConversationOwner(conversationId, userId);
        }
        // 步骤 3：规范化文件名、扩展名、媒体类型和附件展示类型。
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "attachment.bin");
        String fileExt = StrUtil.blankToDefault(FileUtil.extName(fileName), "").toLowerCase(Locale.ROOT);
        String mimeType = StrUtil.blankToDefault(file.getContentType(), "application/octet-stream");
        String attachmentType = mimeType.startsWith("image/") ? "image" : "file";
        byte[] bytes;
        try {
            // 步骤 4：读取上传字节，失败时返回统一中文业务错误。
            bytes = file.getBytes();
        } catch (Exception exception) {
            throw new BusinessException("CHAT_ATTACHMENT_READ_FAILED", ErrorMessageCatalog.CHAT_ATTACHMENT_READ_FAILED);
        }
        // 步骤 5：先写入对象存储，再保存数据库元数据，避免数据库记录指向不存在对象。
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
        // 步骤 6：预览 URL 采用可稳定推导的接口路径，避免前端依赖对象存储私有地址。
        attachment = attachment.toBuilder()
            .previewUrl("/api/chat/attachments/" + attachment.getId() + "/content")
            .build();
        // 步骤 7：保存附件元数据并返回领域对象，响应投影由视图服务完成。
        chatAttachmentRepository.save(attachment);
        return attachment;
    }

    /**
     * 查询附件并校验当前登录用户权限。
     * @param attachmentId 附件主键。
     * @return 附件记录。
     */
    public ChatAttachment requireOwnedAttachment(Long attachmentId) {
        // 步骤 1：读取当前登录用户和附件记录，附件不存在时返回中文未找到错误。
        Long userId = StpUtil.getLoginIdAsLong();
        ChatAttachment attachment = chatAttachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new NotFoundException(ErrorMessageCatalog.CHAT_ATTACHMENT_NOT_FOUND));
        // 步骤 2：只允许上传者读取附件内容，避免通过 ID 枚举访问他人文件。
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
        // 步骤 1：空附件列表直接返回空集合，发送纯文本消息不走后续查询。
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        // 步骤 2：过滤非法 ID 并去重，避免重复绑定同一附件。
        List<Long> normalizedIds = attachmentIds.stream()
            .filter(id -> id != null && id > 0)
            .distinct()
            .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        // 步骤 3：批量读取附件，任一缺失都按部分不存在处理。
        List<ChatAttachment> attachments = chatAttachmentRepository.findByIds(normalizedIds);
        if (attachments.size() != normalizedIds.size()) {
            throw new NotFoundException(ErrorMessageCatalog.CHAT_ATTACHMENT_PARTIAL_NOT_FOUND);
        }
        Set<Long> requestedIdSet = new HashSet<>(normalizedIds);
        // 步骤 4：逐条校验上传人、消息绑定状态和会话归属，保证附件只能被当前消息消费一次。
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
        // 步骤 1：写入会话、消息和执行 run 标识，标记附件已被本次消息消费。
        LocalDateTime now = LocalDateTime.now();
        ChatAttachment persisted = attachment.toBuilder()
            .conversationId(conversationId)
            .messageId(messageId)
            .runId(runId)
            .updatedAt(now)
            .build();
        // 步骤 2：保存绑定后的附件元数据，后续消息列表可按 messageId 查询。
        chatAttachmentRepository.save(persisted);
    }

    /**
     * 查询消息附件列表。
     * @param messageId 消息主键。
     * @return 附件列表。
     */
    public List<ChatAttachment> listByMessageId(Long messageId) {
        // 步骤 1：按消息主键读取已绑定附件，供消息响应投影补齐附件列表。
        return chatAttachmentRepository.findByMessageId(messageId);
    }

    /**
     * 下载附件内容。
     * @param attachment 附件记录。
     * @return 文件字节。
     */
    public byte[] downloadContent(ChatAttachment attachment) {
        try {
            // 步骤 1：按对象存储键下载附件二进制内容。
            return rustFsChatAttachmentClient.download(attachment.getStorageKey());
        } catch (Exception exception) {
            // 步骤 2：下载异常统一转换为业务错误，避免泄露对象存储内部异常。
            throw new BusinessException("CHAT_ATTACHMENT_DOWNLOAD_FAILED", ErrorMessageCatalog.CHAT_ATTACHMENT_DOWNLOAD_FAILED);
        }
    }

    /**
     * 校验附件上传输入，统一处理空文件、大小、扩展名和暂不支持的音视频类型。
     * @param file 上传文件。
     */
    private void validateUploadFile(MultipartFile file) {
        // 步骤 1：空文件直接拒绝，避免后续存储空对象。
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CHAT_ATTACHMENT_EMPTY", ErrorMessageCatalog.CHAT_ATTACHMENT_EMPTY);
        }
        // 步骤 2：使用运行时配置校验大小，便于后台动态调整上传限制。
        long maxFileSize = runtimeSettingService.chatAttachmentMaxFileSizeBytes();
        if (file.getSize() > maxFileSize) {
            throw new BusinessException("CHAT_ATTACHMENT_TOO_LARGE", ErrorMessageCatalog.CHAT_ATTACHMENT_TOO_LARGE);
        }
        // 步骤 3：按扩展名白名单判断是否允许上传。
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "attachment.bin");
        String fileExt = StrUtil.blankToDefault(FileUtil.extName(fileName), "").toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(fileExt) || !ALLOWED_EXTENSIONS.contains(fileExt)) {
            throw new BusinessException("CHAT_ATTACHMENT_TYPE_NOT_ALLOWED", ErrorMessageCatalog.CHAT_ATTACHMENT_TYPE_NOT_ALLOWED);
        }
        // 步骤 4：音视频虽然可能出现在用户侧文件选择中，但当前聊天上下文不支持解析，显式拒绝。
        if (isAudioOrVideo(fileExt)) {
            throw new BusinessException("CHAT_ATTACHMENT_TYPE_NOT_ALLOWED", ErrorMessageCatalog.CHAT_ATTACHMENT_AUDIO_VIDEO_NOT_SUPPORTED);
        }
    }

    /**
     * 判断扩展名是否属于当前聊天上下文暂不支持的音视频类型。
     * @param fileExt 小写文件扩展名。
     * @return 是否为音视频文件。
     */
    private boolean isAudioOrVideo(String fileExt) {
        // 步骤 1：集中维护暂不支持的音视频扩展名，避免散落在上传校验中。
        return Set.of(
            "mp3", "wav", "m4a", "aac", "flac", "ogg",
            "mp4", "mov", "avi", "mkv", "webm", "m4v"
        ).contains(fileExt);
    }

    /**
     * 校验会话归属，防止用户把附件预绑定到他人会话。
     * @param conversationId 会话主键。
     * @param userId 当前登录用户主键。
     */
    private void assertConversationOwner(Long conversationId, Long userId) {
        // 步骤 1：读取会话并比较创建人，附件只能绑定到当前用户自己的会话。
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
        // 步骤 1：空内容无法生成摘要，直接返回 null。
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String rawText = null;
        // 步骤 2：文本类附件按 UTF-8 读取，PDF 使用专用解析器抽取正文。
        if (TEXT_SUMMARY_EXTENSIONS.contains(fileExt)) {
            rawText = StrUtil.trimToEmpty(new String(bytes, CharsetUtil.CHARSET_UTF_8));
        } else if ("pdf".equalsIgnoreCase(fileExt)) {
            rawText = extractPdfText(bytes);
        }
        // 步骤 3：抽取结果为空时不写摘要，避免前端和模型看到无意义空白。
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
            // 步骤 1：用 PDFBox 抽取正文，供模型上下文使用。
            PDFTextStripper pdfTextStripper = new PDFTextStripper();
            return StrUtil.trimToEmpty(pdfTextStripper.getText(document));
        } catch (Exception exception) {
            // 步骤 2：PDF 解析失败不阻断上传，只返回空摘要。
            return "";
        }
    }

    /**
     * 统一归一化摘要文本，清理空白符并执行限长，避免污染模型输入上下文。
     * @param rawText 原始文本。
     * @return 归一化摘要。
     */
    private String normalizeSummaryText(String rawText) {
        // 步骤 1：空文本不生成摘要。
        if (StrUtil.isBlank(rawText)) {
            return null;
        }
        // 步骤 2：清理 NUL 字符和连续空白，避免污染模型提示词。
        String normalizedText = rawText.replace("\u0000", "").replaceAll("\\s+", " ").trim();
        if (StrUtil.isBlank(normalizedText)) {
            return null;
        }
        // 步骤 3：按固定长度截断，控制单个附件进入模型上下文的体积。
        return StrUtil.maxLength(normalizedText, ATTACHMENT_TEXT_SUMMARY_MAX_LENGTH);
    }
}

