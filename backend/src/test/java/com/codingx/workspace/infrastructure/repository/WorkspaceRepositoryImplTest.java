package com.codingx.workspace.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.codingx.common.exception.NotFoundException;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        assertEquals("Workspace not found", exception.getMessage());
    }
}
