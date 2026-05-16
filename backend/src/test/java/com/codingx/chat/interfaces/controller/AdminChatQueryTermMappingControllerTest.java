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

import com.codingx.chat.application.service.AdminChatQueryTermMappingService;
import com.codingx.chat.interfaces.request.QueryTermMappingCreateRequest;
import com.codingx.chat.interfaces.request.QueryTermMappingUpdateRequest;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.chat.interfaces.response.QueryTermMappingResponse;
import com.codingx.config.GlobalExceptionHandler;
import java.time.LocalDateTime;
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
 * 验证关键词映射管理接口契约：分页、详情、新增、更新、删除。
 */
@ExtendWith(MockitoExtension.class)
class AdminChatQueryTermMappingControllerTest {

    @Mock
    private AdminChatQueryTermMappingService adminChatQueryTermMappingService;

    @InjectMocks
    private AdminChatQueryTermMappingController adminChatQueryTermMappingController;

    /**
     * 分页接口应返回 records/total/current/size/pages 结构。
     */
    @Test
    void pageQueryReturnsPaginatedPayload() throws Exception {
        QueryTermMappingResponse row = QueryTermMappingResponse.builder()
            .id(5001L)
            .sourceTerm("语义检索")
            .targetTerm("向量检索")
            .matchType(1)
            .priority(80)
            .enabled(true)
            .remark("中文术语统一")
            .createTime(LocalDateTime.of(2026, 4, 19, 13, 4, 0))
            .updateTime(LocalDateTime.of(2026, 4, 19, 13, 4, 0))
            .build();
        when(adminChatQueryTermMappingService.pageQuery(1, 10, "语义")).thenReturn(
            PageResult.<QueryTermMappingResponse>builder()
                .records(List.of(row))
                .total(1L)
                .current(1L)
                .size(10L)
                .pages(1L)
                .build()
        );

        mockMvc().perform(get("/api/admin/chat/query-term-mappings")
                .param("current", "1")
                .param("size", "10")
                .param("keyword", "语义"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.records[0].id").value(5001))
            .andExpect(jsonPath("$.data.records[0].sourceTerm").value("语义检索"))
            .andExpect(jsonPath("$.data.records[0].matchType").value(1))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.current").value(1))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.pages").value(1));
    }

    /**
     * 详情接口应返回指定主键映射规则。
     */
    @Test
    void queryByIdReturnsSingleRow() throws Exception {
        when(adminChatQueryTermMappingService.queryById(5001L)).thenReturn(
            QueryTermMappingResponse.builder()
                .id(5001L)
                .sourceTerm("prompt")
                .targetTerm("提示词")
                .matchType(1)
                .priority(90)
                .enabled(true)
                .remark("英文术语归一化")
                .build()
        );

        mockMvc().perform(get("/api/admin/chat/query-term-mappings/5001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(5001))
            .andExpect(jsonPath("$.data.sourceTerm").value("prompt"))
            .andExpect(jsonPath("$.data.priority").value(90));
    }

    /**
     * 新增接口应返回创建后的映射规则。
     */
    @Test
    void createReturnsCreatedRow() throws Exception {
        when(adminChatQueryTermMappingService.create(any(QueryTermMappingCreateRequest.class))).thenReturn(
            QueryTermMappingResponse.builder()
                .id(5010L)
                .sourceTerm("embedding")
                .targetTerm("向量嵌入")
                .matchType(1)
                .priority(95)
                .enabled(true)
                .remark("英文术语归一化")
                .build()
        );

        mockMvc().perform(post("/api/admin/chat/query-term-mappings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sourceTerm":"embedding",
                      "targetTerm":"向量嵌入",
                      "matchType":1,
                      "priority":95,
                      "enabled":true,
                      "remark":"英文术语归一化"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(5010))
            .andExpect(jsonPath("$.data.sourceTerm").value("embedding"));
    }

    /**
     * 更新接口应返回最新映射规则。
     */
    @Test
    void updateReturnsUpdatedRow() throws Exception {
        when(adminChatQueryTermMappingService.update(eq(5001L), any(QueryTermMappingUpdateRequest.class))).thenReturn(
            QueryTermMappingResponse.builder()
                .id(5001L)
                .sourceTerm("语义搜索")
                .targetTerm("向量检索")
                .matchType(1)
                .priority(81)
                .enabled(true)
                .remark("中文术语统一")
                .build()
        );

        mockMvc().perform(put("/api/admin/chat/query-term-mappings/5001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "sourceTerm":"语义搜索",
                      "targetTerm":"向量检索",
                      "matchType":1,
                      "priority":81,
                      "enabled":true,
                      "remark":"中文术语统一"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(5001))
            .andExpect(jsonPath("$.data.sourceTerm").value("语义搜索"))
            .andExpect(jsonPath("$.data.priority").value(81));
    }

    /**
     * 删除接口应调用服务并返回统一成功文案。
     */
    @Test
    void deleteReturnsSuccessMessage() throws Exception {
        mockMvc().perform(delete("/api/admin/chat/query-term-mappings/5001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("删除成功"));

        verify(adminChatQueryTermMappingService).delete(5001L);
    }

    /**
     * 构造测试用 MockMvc 并挂载全局异常处理器。
     * @return MockMvc。
     */
    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(adminChatQueryTermMappingController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }
}
