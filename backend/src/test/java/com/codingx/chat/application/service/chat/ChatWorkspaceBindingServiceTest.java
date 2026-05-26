package com.codingx.chat.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cn.dev33.satoken.stp.StpUtil;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
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
 * 验证本地仓库绑定仅建立本机执行上下文，不再创建云端工作空间记录。
 */
@ExtendWith(MockitoExtension.class)
class ChatWorkspaceBindingServiceTest {

    @Mock
    private WorkspaceMapper workspaceMapper;

    @InjectMocks
    private ChatWorkspaceBindingService chatWorkspaceBindingService;

    /**
     * 绑定本地仓库目录时只返回本地路径与名称，不应产生数据库 workspaceId。
     */
    @Test
    @SuppressWarnings("unchecked")
    void bindRepositoryPathForCurrentUserDoesNotCreateCloudWorkspace(@TempDir Path tempDir) throws Exception {
        Path repoDir = Files.createDirectory(tempDir.resolve("repo"));
        try (MockedStatic<StpUtil> mocked = Mockito.mockStatic(StpUtil.class)) {
            mocked.when(StpUtil::getLoginIdAsLong).thenReturn(1002L);

            ChatWorkspaceBindingService.WorkspaceBindingResult result =
                chatWorkspaceBindingService.bindRepositoryPathForCurrentUser(repoDir.toString());

            verify(workspaceMapper, never()).selectOne(any());
            verify(workspaceMapper, never()).insert(any(WorkspaceDO.class));

            assertNull(result.workspaceId());
            assertEquals(repoDir.toString().replace('\\', '/'), result.repositoryPath());
            assertEquals(repoDir.getFileName().toString(), result.workspaceName());
        }
    }
}
