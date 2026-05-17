package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.AdminChatSkillService;
import com.codingx.chat.domain.model.ChatSkill;
import com.codingx.config.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证管理端技能管理接口的 HTTP 契约。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatSkillControllerTest {

    @Mock
    private AdminChatSkillService adminChatSkillService;

    @InjectMocks
    private AdminChatSkillController adminChatSkillController;

    /**
     * 列表接口应返回技能基础字段。
     */
    @Test
    void listSkillsReturnsSkillRows() throws Exception {
        when(adminChatSkillService.listAll()).thenReturn(List.of(
            ChatSkill.builder()
                .id(7101L)
                .skillCode("conversation-core")
                .displayName("会话核心")
                .category("核心能力")
                .enabled(1)
                .sortNo(1)
                .build()
        ));

        mockMvc().perform(get("/api/admin/chat/skills"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].skillCode").value("conversation-core"))
            .andExpect(jsonPath("$.data[0].displayName").value("会话核心"))
            .andExpect(jsonPath("$.data[0].category").value("核心能力"));
    }

    /**
     * 创建接口应返回新增后的技能记录。
     */
    @Test
    void createSkillReturnsCreatedSkill() throws Exception {
        when(adminChatSkillService.create(any(ChatSkill.class))).thenReturn(
            ChatSkill.builder()
                .id(7109L)
                .skillCode("my-skill")
                .displayName("我的技能")
                .category("自定义")
                .enabled(1)
                .sortNo(9)
                .build()
        );

        mockMvc().perform(post("/api/admin/chat/skills")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "skillCode":"my-skill",
                      "displayName":"我的技能",
                      "category":"自定义",
                      "enabled":1,
                      "sortNo":9
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(7109))
            .andExpect(jsonPath("$.data.skillCode").value("my-skill"));
    }

    /**
     * 更新接口应按路径主键更新并返回结果。
     */
    @Test
    void updateSkillReturnsUpdatedSkill() throws Exception {
        when(adminChatSkillService.update(eq(7101L), any(ChatSkill.class))).thenReturn(
            ChatSkill.builder()
                .id(7101L)
                .skillCode("conversation-core")
                .displayName("会话核心升级")
                .category("核心能力")
                .enabled(1)
                .sortNo(1)
                .build()
        );

        mockMvc().perform(put("/api/admin/chat/skills/7101")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "skillCode":"conversation-core",
                      "displayName":"会话核心升级",
                      "category":"核心能力",
                      "enabled":1,
                      "sortNo":1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(7101))
            .andExpect(jsonPath("$.data.displayName").value("会话核心升级"));
    }

    /**
     * 删除接口应返回统一成功文案。
     */
    @Test
    void deleteSkillReturnsSuccessMessage() throws Exception {
        mockMvc().perform(delete("/api/admin/chat/skills/7101"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("删除成功"));

        verify(adminChatSkillService).delete(7101L);
    }

    /**
     * 上传技能包接口应按 multipart 契约返回技能记录。
     */
    @Test
    void uploadSkillPackageReturnsParsedSkill() throws Exception {
        when(adminChatSkillService.uploadSkillPackage(any(), any())).thenReturn(
            ChatSkill.builder()
                .id(7110L)
                .skillCode("pdf-processing")
                .displayName("pdf-processing")
                .description("处理 PDF 文档")
                .sourceType("uploaded")
                .enabled(1)
                .sortNo(0)
                .build()
        );

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "pdf-processing.skill",
            "application/octet-stream",
            "dummy".getBytes()
        );

        mockMvc().perform(multipart("/api/admin/chat/skills/upload")
                .file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.skillCode").value("pdf-processing"))
            .andExpect(jsonPath("$.data.sourceType").value("uploaded"));
    }

    /**
     * 创建 MockMvc 并挂载全局异常处理器，保证错误响应格式一致。
     * @return 测试用 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatSkillController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
