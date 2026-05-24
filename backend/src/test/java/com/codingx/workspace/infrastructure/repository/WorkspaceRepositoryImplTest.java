package com.codingx.workspace.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证 WorkspaceRepositoryImpl 的工作空间存在性校验逻辑。
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceRepositoryImplTest {

    @Mock
    private WorkspaceMapper workspaceMapper;

    @InjectMocks
    private WorkspaceRepositoryImpl workspaceRepository;

    /**
     * 存在且未删除的工作空间应通过校验。
     */
    @Test
    void ensureExistsPassesWhenWorkspaceExists() {
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(3001L);
        workspace.setDeleted(0);
        when(workspaceMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(workspace);

        assertDoesNotThrow(() -> workspaceRepository.ensureExists(3001L));
    }

    /**
     * 工作空间不存在时应抛出 404 语义异常。
     */
    @Test
    void ensureExistsThrowsNotFoundWhenWorkspaceMissing() {
        when(workspaceMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);

        NotFoundException exception = assertThrows(NotFoundException.class, () -> workspaceRepository.ensureExists(3001L));
        assertEquals(ErrorMessageCatalog.WORKSPACE_NOT_FOUND, exception.getMessage());
    }

    /**
     * 创建默认云端工作空间时只应依赖 runtime_target，不应再回写旧 workspace_type 列。
     */
    @Test
    @SuppressWarnings("unchecked")
    void ensureDefaultCloudWorkspaceUsesRuntimeTargetOnly() {
        when(workspaceMapper.selectOne(any())).thenReturn(null);
        doReturn(1).when(workspaceMapper).insert(any(WorkspaceDO.class));

        WorkspaceDO workspace = workspaceRepository.ensureDefaultCloudWorkspace(1001L, "CodingX Admin");

        verify(workspaceMapper).selectOne(any());
        ArgumentCaptor<WorkspaceDO> workspaceCaptor = ArgumentCaptor.forClass(WorkspaceDO.class);
        verify(workspaceMapper).insert(workspaceCaptor.capture());

        WorkspaceDO inserted = workspaceCaptor.getValue();
        assertEquals("cloud", inserted.getRuntimeTarget());
        assertNull(inserted.getWorkspaceType());
        assertEquals("历史记录", inserted.getName());
        assertEquals(1001L, inserted.getCreatedBy());
        assertEquals(workspace.getId(), inserted.getId());
    }

    /**
     * 默认云端空间读取接口应仅返回已有记录，不应隐式创建新空间。
     */
    @Test
    void findDefaultCloudWorkspaceByUserIdReturnsExistingWorkspaceWithoutCreating() {
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(9001L);
        workspace.setRuntimeTarget(WorkspaceRepositoryImpl.RUNTIME_TARGET_CLOUD);
        workspace.setName("历史记录");
        when(workspaceMapper.selectOne(any())).thenReturn(workspace);

        Optional<WorkspaceDO> result = workspaceRepository.findDefaultCloudWorkspaceByUserId(1001L);

        assertTrue(result.isPresent());
        assertEquals(9001L, result.get().getId());
        verify(workspaceMapper).selectOne(any());
    }
}
