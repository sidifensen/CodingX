package com.codingx.chat.interfaces.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.skill.application.service.ChatSkillQueryService;
import com.codingx.skill.domain.model.ChatSkill;
import com.codingx.skill.interfaces.controller.ChatSkillController;
import com.codingx.config.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证用户侧聊天技能查询接口契约。
 */
@ExtendWith(MockitoExtension.class)
class ChatSkillControllerTest {

    @Mock
    private ChatSkillQueryService chatSkillQueryService;

    @InjectMocks
    private ChatSkillController chatSkillController;

    /**
     * 用户侧技能接口应返回启用技能清单。
     */
    @Test
    void listEnabledSkillsReturnsRows() throws Exception {
        when(chatSkillQueryService.listEnabledSkills()).thenReturn(List.of(
            ChatSkill.builder()
                .id(7101L)
                .skillCode("conversation-core")
                .displayName("会话核心")
                .category("核心能力")
                .enabled(1)
                .sortNo(1)
                .build()
        ));

        mockMvc().perform(get("/api/skills"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].skillCode").value("conversation-core"))
            .andExpect(jsonPath("$.data[0].displayName").value("会话核心"));
    }

    /**
     * 构造测试用 MockMvc 并启用全局异常处理。
     * @return MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(chatSkillController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
