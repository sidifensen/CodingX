package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.admin.application.service.AdminWorkspaceService;
import com.codingx.chat.domain.model.ChatConversation;
import com.codingx.chat.domain.model.ChatConversationStatus;
import com.codingx.chat.domain.repository.ChatConversationRepository;
import com.codingx.chat.interfaces.response.AdminChatConversationListItemResponse;
import com.codingx.chat.interfaces.response.PageResult;
import com.codingx.workspace.domain.model.AdminWorkspacePage;
import com.codingx.workspace.domain.model.AdminWorkspaceQuery;
import com.codingx.workspace.domain.model.AdminWorkspaceRecord;
import com.codingx.workspace.domain.repository.WorkspaceRepository;
import com.codingx.workspace.interfaces.response.AdminWorkspaceListItemResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证管理端工作空间服务的分页映射与运行目标文案。
 */
@ExtendWith(MockitoExtension.class)
class AdminWorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ChatConversationRepository chatConversationRepository;

    @InjectMocks
    private AdminWorkspaceService adminWorkspaceService;

    /**
     * 列表查询应归一化分页参数，并将运行目标转换为中文展示标签。
     */
    @Test
    void pageWorkspacesMapsRuntimeTargetLabels() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 0, 35, 0);
        when(workspaceRepository.pageForAdmin(new AdminWorkspaceQuery(1, 10, "项目", "local"))).thenReturn(
            new AdminWorkspacePage(
                List.of(new AdminWorkspaceRecord(
                    3001L,
                    "本地项目",
                    "https://example.com/codingx.git",
                    "main",
                    "D:/code/CodingX",
                    "local",
                    1001L,
                    2L,
                    now,
                    now
                )),
                1L,
                10L,
                1L,
                1L
            )
        );

        PageResult<AdminWorkspaceListItemResponse> result = adminWorkspaceService.pageWorkspaces(1, 10, "项目", "local");

        assertEquals(1L, result.total());
        assertEquals(1L, result.current());
        assertEquals("本地项目", result.records().getFirst().name());
        assertEquals("local", result.records().getFirst().runtimeTarget());
        assertEquals("本地", result.records().getFirst().runtimeTargetLabel());
        assertEquals(2L, result.records().getFirst().conversationCount());
    }

    /**
     * 工作空间详情页使用的会话查询必须先校验空间存在，再只返回该空间内的会话分页。
     */
    @Test
    void pageWorkspaceConversationsValidatesWorkspaceAndMapsRows() {
        ChatConversation conversation = ChatConversation.create(
            2001L,
            "本地项目会话",
            1002L,
            3001L,
            ChatConversationStatus.ACTIVE
        );
        conversation.restoreRuntimeState(LocalDateTime.of(2026, 5, 25, 10, 2, 0), 5001L);
        conversation.restorePersistenceState(
            LocalDateTime.of(2026, 5, 25, 10, 0, 0),
            LocalDateTime.of(2026, 5, 25, 10, 5, 0)
        );
        when(chatConversationRepository.findAllByWorkspaceId(3001L, "项目")).thenReturn(List.of(conversation));

        PageResult<AdminChatConversationListItemResponse> result =
            adminWorkspaceService.pageWorkspaceConversations(3001L, 1, 10, "项目");

        verify(workspaceRepository).ensureExists(3001L);
        assertEquals(1L, result.total());
        assertEquals(1L, result.current());
        assertEquals(2001L, result.records().getFirst().id());
        assertEquals("本地项目会话", result.records().getFirst().title());
        assertEquals("活跃", result.records().getFirst().statusLabel());
    }
}
