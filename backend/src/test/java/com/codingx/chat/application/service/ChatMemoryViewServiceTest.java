package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.codingx.chat.interfaces.response.ChatLongTermMemoryResponse;
import com.codingx.governance.domain.model.GovernanceLongTermMemory;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证长期记忆响应投影规则，确保用户端能展示可信的工作空间名称。
 */
@ExtendWith(MockitoExtension.class)
class ChatMemoryViewServiceTest {

    /** 工作空间仓储，用于按当前用户补齐长期记忆所属空间名称。 */
    @Mock
    private WorkspaceRepositoryImpl workspaceRepositoryImpl;

    /** 长期记忆视图服务，被测对象。 */
    @InjectMocks
    private ChatMemoryViewService chatMemoryViewService;

    /**
     * 项目记忆响应应携带 workspace 表中的真实名称，避免前端从本地历史分组猜错名称。
     */
    @Test
    void toMemoryResponseReturnsOwnedWorkspaceName() {
        GovernanceLongTermMemory memory = GovernanceLongTermMemory.builder()
            .id(9001L)
            .memoryScope("PROJECT")
            .userId(1002L)
            .workspaceId(3001L)
            .content("test 工作空间的项目记忆")
            .status("ACTIVE")
            .build();
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(3001L);
        workspace.setName("test");
        workspace.setCreatedBy(1002L);
        when(workspaceRepositoryImpl.findOwnedWorkspaceById(3001L, 1002L)).thenReturn(Optional.of(workspace));

        ChatLongTermMemoryResponse response = chatMemoryViewService.toMemoryResponse(memory, 1002L);

        assertEquals(9001L, response.id());
        assertEquals(3001L, response.workspaceId());
        assertEquals("test", response.workspaceName());
        assertEquals("test 工作空间的项目记忆", response.content());
    }

    /**
     * 用户级记忆不绑定工作空间，响应应保持 workspaceName 为空，避免误显示当前项目。
     */
    @Test
    void toMemoryResponsesKeepUserMemoryWorkspaceNameEmpty() {
        GovernanceLongTermMemory memory = GovernanceLongTermMemory.builder()
            .id(9002L)
            .memoryScope("USER")
            .userId(1002L)
            .workspaceId(null)
            .content("以后都先给结论")
            .status("ACTIVE")
            .build();

        List<ChatLongTermMemoryResponse> responses = chatMemoryViewService.toMemoryResponses(List.of(memory), 1002L);

        assertEquals(1, responses.size());
        assertNull(responses.get(0).workspaceId());
        assertNull(responses.get(0).workspaceName());
    }
}
