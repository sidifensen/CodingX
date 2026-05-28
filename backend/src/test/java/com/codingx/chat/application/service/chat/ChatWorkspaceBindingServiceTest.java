package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import com.codingx.workspace.infrastructure.repository.WorkspaceRepositoryImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证本地仓库绑定会创建或复用云端记录，确保本地会话也能统一归档和审计。
 */
@ExtendWith(MockitoExtension.class)
class ChatWorkspaceBindingServiceTest {

    @Mock
    private WorkspaceMapper workspaceMapper;

    @Mock
    private WorkspaceRepositoryImpl workspaceRepositoryImpl;

    @InjectMocks
    private ChatWorkspaceBindingService chatWorkspaceBindingService;

    /**
     * 绑定本地仓库目录时应创建本地 workspace，并返回后续聊天可挂载的 workspaceId。
     */
    @Test
    @SuppressWarnings("unchecked")
    void bindRepositoryPathForCurrentUserCreatesLocalWorkspace(@TempDir Path tempDir) throws Exception {
        Path repoDir = Files.createDirectory(tempDir.resolve("repo"));
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(8201L);
        workspace.setName(repoDir.getFileName().toString());
        workspace.setWorkingDirectory(repoDir.toString().replace('\\', '/'));
        workspace.setRuntimeTarget("local");
        workspace.setCreatedBy(1002L);
        when(workspaceRepositoryImpl.ensureLocalWorkspace(
            1002L,
            repoDir.toString().replace('\\', '/'),
            repoDir.getFileName().toString()
        )).thenReturn(workspace);
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ChatWorkspaceBindingService.WorkspaceBindingResult result =
                chatWorkspaceBindingService.bindRepositoryPathForCurrentUser(repoDir.toString());

            verify(workspaceRepositoryImpl).ensureLocalWorkspace(
                1002L,
                repoDir.toString().replace('\\', '/'),
                repoDir.getFileName().toString()
            );
            verify(workspaceMapper, Mockito.never()).insert(any(WorkspaceDO.class));
            assertEquals(8201L, result.workspaceId());
            assertEquals(repoDir.toString().replace('\\', '/'), result.repositoryPath());
            assertEquals(repoDir.getFileName().toString(), result.workspaceName());
        }
    }

    /**
     * 已存在同一路径本地 workspace 时应直接复用，避免重复出现多个同名项目。
     */
    @Test
    @SuppressWarnings("unchecked")
    void bindRepositoryPathForCurrentUserReusesExistingLocalWorkspace(@TempDir Path tempDir) throws Exception {
        Path repoDir = Files.createDirectory(tempDir.resolve("repo"));
        WorkspaceDO existingWorkspace = new WorkspaceDO();
        existingWorkspace.setId(8301L);
        existingWorkspace.setName("repo");
        existingWorkspace.setWorkingDirectory(repoDir.toString().replace('\\', '/'));
        existingWorkspace.setRuntimeTarget("local");
        existingWorkspace.setCreatedBy(1002L);
        existingWorkspace.setDeleted(0);
        when(workspaceRepositoryImpl.ensureLocalWorkspace(
            1002L,
            repoDir.toString().replace('\\', '/'),
            repoDir.getFileName().toString()
        )).thenReturn(existingWorkspace);
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ChatWorkspaceBindingService.WorkspaceBindingResult result =
                chatWorkspaceBindingService.bindRepositoryPathForCurrentUser(repoDir.toString());

            assertEquals(8301L, result.workspaceId());
            assertEquals("repo", result.workspaceName());
            assertEquals(repoDir.toString().replace('\\', '/'), result.repositoryPath());
            verify(workspaceMapper, Mockito.never()).insert(any(WorkspaceDO.class));
        }
    }
}
