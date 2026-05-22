package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.chat.domain.model.ChatAttachment;
import com.codingx.chat.domain.repository.ChatAttachmentRepository;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.common.storage.RustFsChatAttachmentClient;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 验证聊天附件上传后摘要抽取行为，确保文本与 PDF 文件都能生成可注入模型的上下文。
 */
@ExtendWith(MockitoExtension.class)
class ChatAttachmentServiceTest {

    @Mock
    private ChatAttachmentRepository chatAttachmentRepository;

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @Mock
    private RustFsChatAttachmentClient rustFsChatAttachmentClient;

    @InjectMocks
    private ChatAttachmentService chatAttachmentService;

    /**
     * 上传纯文本文件时应写入归一化摘要，避免空白符噪音进入模型提示。
     */
    @Test
    void uploadExtractsNormalizedSummaryForTextFile() {
        when(rustFsChatAttachmentClient.upload(any(), eq("resume.txt"), eq("text/plain")))
            .thenReturn("storage/key/resume.txt");
        try (MockedStatic<StpUtil> mockedStatic = Mockito.mockStatic(StpUtil.class)) {
            mockedStatic.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);
            MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "Java  Developer\n\n5 years   experience".getBytes(StandardCharsets.UTF_8)
            );

            ChatAttachment attachment = chatAttachmentService.upload(multipartFile, null);

            assertNotNull(attachment.getId());
            assertEquals("file", attachment.getAttachmentType());
            assertEquals("Java Developer 5 years experience", attachment.getContentSummary());
            verify(chatAttachmentRepository).save(any(ChatAttachment.class));
        }
    }

    /**
     * 上传 PDF 文件时应解析正文并写入摘要，避免“文件已上传但模型看不到内容”。
     */
    @Test
    void uploadExtractsSummaryForPdfFile() throws Exception {
        when(rustFsChatAttachmentClient.upload(any(), eq("resume.pdf"), eq("application/pdf")))
            .thenReturn("storage/key/resume.pdf");
        try (MockedStatic<StpUtil> mockedStatic = Mockito.mockStatic(StpUtil.class)) {
            mockedStatic.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);
            MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                createPdfBytes("Candidate skills: Java Spring Boot distributed systems")
            );

            chatAttachmentService.upload(multipartFile, null);

            ArgumentCaptor<ChatAttachment> attachmentCaptor = ArgumentCaptor.forClass(ChatAttachment.class);
            verify(chatAttachmentRepository).save(attachmentCaptor.capture());
            ChatAttachment savedAttachment = attachmentCaptor.getValue();
            assertNotNull(savedAttachment.getContentSummary());
            assertTrue(savedAttachment.getContentSummary().contains("Java"));
            assertTrue(savedAttachment.getContentSummary().contains("Spring"));
        }
    }

    /**
     * 生成最小 PDF 测试样本，覆盖附件服务的 PDF 摘要解析分支。
     * @param text 需要写入 PDF 的正文文本。
     * @return PDF 文件字节。
     * @throws Exception 生成失败时抛出异常。
     */
    private byte[] createPdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
