package com.codingx.chat.interfaces.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codingx.chat.application.service.AdminChatIntentService;
import com.codingx.chat.domain.model.ChatIntentNode;
import com.codingx.config.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 验证管理端意图树 HTTP 契约，确保前端配置台可按 ragent 字段读写节点。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatIntentControllerTest {

    @Mock
    private AdminChatIntentService adminChatIntentService;

    @InjectMocks
    private AdminChatIntentController adminChatIntentController;

    /**
     * 树形接口应返回 children 与 ragent 兼容字段，供左侧树和右侧详情复用同一载体。
     */
    @Test
    void getTreeReturnsNestedIntentNodes() throws Exception {
        when(adminChatIntentService.listTree()).thenReturn(List.of(
            ChatIntentNode.builder()
                .id(3001L)
                .intentCode("group")
                .name("集团信息化")
                .kind(0)
                .intentType("kb")
                .children(List.of(ChatIntentNode.builder().id(3002L).intentCode("group-hr").name("人事").kind(0).intentType("kb").build()))
                .build()
        ));

        mockMvc().perform(get("/api/admin/chat/intents/tree"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].intentCode").value("group"))
            .andExpect(jsonPath("$.data[0].kind").value(0))
            .andExpect(jsonPath("$.data[0].intentType").value("kb"))
            .andExpect(jsonPath("$.data[0].children[0].intentCode").value("group-hr"));
    }

    /**
     * 创建接口沿用 POST 路径，同时返回服务派生后的 kind 与 intentType。
     */
    @Test
    void postCreatesIntentNode() throws Exception {
        when(adminChatIntentService.save(any(ChatIntentNode.class))).thenReturn(
            ChatIntentNode.builder().id(9001L).intentCode("sys-help").name("帮助").kind(1).intentType("system").build()
        );

        mockMvc().perform(post("/api/admin/chat/intents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"intentCode\":\"sys-help\",\"name\":\"帮助\",\"kind\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(9001))
            .andExpect(jsonPath("$.data.intentType").value("system"))
            .andExpect(jsonPath("$.data.kind").value(1));
    }

    /**
     * 更新接口必须通过路径 ID 定位节点，避免前端表单体中的 ID 污染目标记录。
     */
    @Test
    void putUpdatesIntentNodeByPathId() throws Exception {
        when(adminChatIntentService.update(eq(3002L), any(ChatIntentNode.class))).thenReturn(
            ChatIntentNode.builder().id(3002L).intentCode("group-hr").name("人力资源").kind(0).intentType("kb").build()
        );

        mockMvc().perform(put("/api/admin/chat/intents/3002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":9999,\"intentCode\":\"group-hr\",\"name\":\"人力资源\",\"kind\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(3002))
            .andExpect(jsonPath("$.data.name").value("人力资源"));
    }

    /**
     * 删除接口只做逻辑删除，响应成功文案供前端弹窗关闭后提示使用。
     */
    @Test
    void deleteSoftDeletesIntentNode() throws Exception {
        mockMvc().perform(delete("/api/admin/chat/intents/3002"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("删除成功"));

        verify(adminChatIntentService).delete(3002L);
    }

    /**
     * 延迟创建 MockMvc，确保 Mockito 完成被测控制器注入并启用统一异常响应。
     * @return 可执行 HTTP 契约断言的 MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatIntentController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
