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

import com.codingx.skill.application.service.AdminChatSkillService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.skill.interfaces.controller.AdminChatSkillController;
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
        when(adminChatSkillService.pageSkills(1, 10)).thenReturn(PageResult.<ChatSkill>builder()
            .records(List.of(
                ChatSkill.builder()
                    .id(7101L)
                    .skillCode("conversation-core")
                    .displayName("会话核心")
                    .category("核心能力")
                    .enabled(1)
                    .sortNo(1)
                    .build()
            ))
            .total(1L)
            .size(10L)
            .current(1L)
            .pages(1L)
            .build());

        mockMvc().perform(get("/api/admin/skills"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].skillCode").value("conversation-core"))
            .andExpect(jsonPath("$.data.records[0].displayName").value("会话核心"))
            .andExpect(jsonPath("$.data.records[0].category").value("核心能力"))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.current").value(1))
            .andExpect(jsonPath("$.data.pages").value(1));
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

        mockMvc().perform(post("/api/admin/skills")
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

        mockMvc().perform(put("/api/admin/skills/7101")
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
        mockMvc().perform(delete("/api/admin/skills/7101"))
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

        mockMvc().perform(multipart("/api/admin/skills/upload")
                .file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.skillCode").value("pdf-processing"))
            .andExpect(jsonPath("$.data.sourceType").value("uploaded"));
    }

    /**
     * 技能包目录树接口应返回目录与文件条目。
     */
    @Test
    void listPackageEntriesReturnsResourceTree() throws Exception {
        when(adminChatSkillService.listPackageEntries(7110L)).thenReturn(List.of(
            new AdminChatSkillService.SkillPackageEntry("templates", "templates", true, null),
            new AdminChatSkillService.SkillPackageEntry("templates/prompt.txt", "prompt.txt", false, 128L)
        ));

        mockMvc().perform(get("/api/admin/skills/7110/package/entries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].path").value("templates"))
            .andExpect(jsonPath("$.data[0].directory").value(true))
            .andExpect(jsonPath("$.data[1].name").value("prompt.txt"))
            .andExpect(jsonPath("$.data[1].size").value(128));
    }

    /**
     * 技能包文件内容接口应返回文本内容与截断标识。
     */
    @Test
    void readPackageFileContentReturnsTextPreview() throws Exception {
        when(adminChatSkillService.readPackageFileContent(7110L, "templates/prompt.txt"))
            .thenReturn(new AdminChatSkillService.SkillPackageFileContent(
                "templates/prompt.txt",
                "prompt-content",
                false
            ));

        mockMvc().perform(get("/api/admin/skills/7110/package/file-content")
                .param("path", "templates/prompt.txt"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.path").value("templates/prompt.txt"))
            .andExpect(jsonPath("$.data.content").value("prompt-content"))
            .andExpect(jsonPath("$.data.truncated").value(false));
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
