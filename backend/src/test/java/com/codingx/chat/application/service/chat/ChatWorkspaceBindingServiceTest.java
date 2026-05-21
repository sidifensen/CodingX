package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证仓库绑定逻辑只依赖 runtime_target，不再引用已下线的 workspace_type 列。
 */
@ExtendWith(MockitoExtension.class)
class ChatWorkspaceBindingServiceTest {

    @Mock
    private WorkspaceMapper workspaceMapper;

    @InjectMocks
    private ChatWorkspaceBindingService chatWorkspaceBindingService;

    /**
     * 绑定本地仓库目录时应只写 runtime_target，并返回规范化路径与空间名称。
     */
    @Test
    @SuppressWarnings("unchecked")
    void bindRepositoryPathForCurrentUserUsesRuntimeTargetOnly(@TempDir Path tempDir) throws Exception {
        when(workspaceMapper.selectOne(any())).thenReturn(null);
        doReturn(1).when(workspaceMapper).insert(any(WorkspaceDO.class));

        Path repoDir = Files.createDirectory(tempDir.resolve("repo"));
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ChatWorkspaceBindingService.WorkspaceBindingResult result =
                chatWorkspaceBindingService.bindRepositoryPathForCurrentUser(repoDir.toString());

            verify(workspaceMapper).selectOne(any());
            ArgumentCaptor<WorkspaceDO> workspaceCaptor = ArgumentCaptor.forClass(WorkspaceDO.class);
            verify(workspaceMapper).insert(workspaceCaptor.capture());

            assertEquals("local", workspaceCaptor.getValue().getRuntimeTarget());
            assertNull(workspaceCaptor.getValue().getWorkspaceType());
            assertEquals(repoDir.toString().replace('\\', '/'), result.repositoryPath());
            assertEquals(repoDir.getFileName().toString(), result.workspaceName());
        }
    }
}
