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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codingx.common.error.ErrorMessageCatalog;
import com.codingx.common.exception.NotFoundException;
import com.codingx.chat.infrastructure.persistence.mapper.ChatConversationMapper;
import com.codingx.workspace.domain.model.AdminWorkspacePage;
import com.codingx.workspace.domain.model.AdminWorkspaceQuery;
import com.codingx.workspace.infrastructure.persistence.dataobject.WorkspaceDO;
import com.codingx.workspace.infrastructure.persistence.mapper.WorkspaceMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
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

    /**
     * 纯 Mockito 单测不会启动 MyBatis 容器，需要手动初始化表元数据才能检查 LambdaQueryWrapper 的列名条件。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
            new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""),
            WorkspaceDO.class
        );
    }

    @Mock
    private WorkspaceMapper workspaceMapper;

    @Mock
    private ChatConversationMapper chatConversationMapper;

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
        assertEquals("云端历史记录", inserted.getName());
        assertEquals(1001L, inserted.getCreatedBy());
        assertEquals(workspace.getId(), inserted.getId());
    }

    /**
     * 创建默认本地历史空间时应使用 local 运行目标且不写入目录路径。
     */
    @Test
    @SuppressWarnings("unchecked")
    void ensureDefaultLocalWorkspaceCreatesLocalHistoryWorkspace() {
        when(workspaceMapper.selectOne(any())).thenReturn(null);
        doReturn(1).when(workspaceMapper).insert(any(WorkspaceDO.class));

        WorkspaceDO workspace = workspaceRepository.ensureDefaultLocalWorkspace(1001L);

        ArgumentCaptor<WorkspaceDO> workspaceCaptor = ArgumentCaptor.forClass(WorkspaceDO.class);
        verify(workspaceMapper).insert(workspaceCaptor.capture());
        WorkspaceDO inserted = workspaceCaptor.getValue();
        assertEquals("local", inserted.getRuntimeTarget());
        assertEquals("本地历史记录", inserted.getName());
        assertNull(inserted.getWorkingDirectory());
        assertEquals(1001L, inserted.getCreatedBy());
        assertEquals(workspace.getId(), inserted.getId());
    }

    /**
     * 绑定本地目录时应按规范化路径创建 local workspace，后续会话可直接挂载。
     */
    @Test
    @SuppressWarnings("unchecked")
    void ensureLocalWorkspaceCreatesWorkspaceForPath() {
        when(workspaceMapper.selectOne(any())).thenReturn(null);
        doReturn(1).when(workspaceMapper).insert(any(WorkspaceDO.class));

        WorkspaceDO workspace = workspaceRepository.ensureLocalWorkspace(1001L, "D:/code/test", "test");

        ArgumentCaptor<WorkspaceDO> workspaceCaptor = ArgumentCaptor.forClass(WorkspaceDO.class);
        verify(workspaceMapper).insert(workspaceCaptor.capture());
        WorkspaceDO inserted = workspaceCaptor.getValue();
        assertEquals("local", inserted.getRuntimeTarget());
        assertEquals("test", inserted.getName());
        assertEquals("D:/code/test", inserted.getWorkingDirectory());
        assertEquals(1001L, inserted.getCreatedBy());
        assertEquals(workspace.getId(), inserted.getId());
    }

    /**
     * 用户侧工作区库存只应返回当前用户未删除的工作空间，供侧栏展示空工作区分组。
     */
    @Test
    @SuppressWarnings("unchecked")
    void listActiveWorkspacesByUserReturnsOnlyCurrentUserActiveWorkspaces() {
        WorkspaceDO localWorkspace = new WorkspaceDO();
        localWorkspace.setId(3001L);
        localWorkspace.setName("CodingX");
        localWorkspace.setWorkingDirectory("D:/code/CodingX");
        localWorkspace.setRuntimeTarget(WorkspaceRepositoryImpl.RUNTIME_TARGET_LOCAL);
        localWorkspace.setCreatedBy(1001L);
        localWorkspace.setDeleted(0);
        WorkspaceDO cloudWorkspace = new WorkspaceDO();
        cloudWorkspace.setId(3002L);
        cloudWorkspace.setName("云端历史记录");
        cloudWorkspace.setRuntimeTarget(WorkspaceRepositoryImpl.RUNTIME_TARGET_CLOUD);
        cloudWorkspace.setCreatedBy(1001L);
        cloudWorkspace.setDeleted(0);
        when(workspaceMapper.selectList(any())).thenReturn(List.of(localWorkspace, cloudWorkspace));

        List<WorkspaceDO> workspaces = workspaceRepository.listActiveWorkspacesByUser(1001L);

        assertEquals(2, workspaces.size());
        assertEquals(3001L, workspaces.get(0).getId());
        assertEquals("CodingX", workspaces.get(0).getName());
        assertEquals("D:/code/CodingX", workspaces.get(0).getWorkingDirectory());

        // 关键断言：库存接口必须在仓储层带上当前用户和未删除过滤，避免用户端看到管理端全量工作区。
        ArgumentCaptor<LambdaQueryWrapper<WorkspaceDO>> captor =
            ArgumentCaptor.forClass((Class<LambdaQueryWrapper<WorkspaceDO>>) (Class<?>) LambdaQueryWrapper.class);
        verify(workspaceMapper).selectList(captor.capture());
        LambdaQueryWrapper<WorkspaceDO> wrapper = captor.getValue();
        String sqlSegment = wrapper.getCustomSqlSegment().toLowerCase(Locale.ROOT);
        assertTrue(sqlSegment.contains("created_by ="), sqlSegment);
        assertTrue(sqlSegment.contains("deleted ="), sqlSegment);
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1001L));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(0));
    }

    /**
     * 默认云端空间读取接口应仅返回已有记录，不应隐式创建新空间。
     */
    @Test
    void findDefaultCloudWorkspaceByUserIdReturnsExistingWorkspaceWithoutCreating() {
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(9001L);
        workspace.setRuntimeTarget(WorkspaceRepositoryImpl.RUNTIME_TARGET_CLOUD);
        workspace.setName("云端历史记录");
        when(workspaceMapper.selectOne(any())).thenReturn(workspace);

        Optional<WorkspaceDO> result = workspaceRepository.findDefaultCloudWorkspaceByUserId(1001L);

        assertTrue(result.isPresent());
        assertEquals(9001L, result.get().getId());
        verify(workspaceMapper).selectOne(any());
    }

    /**
     * 管理端分页查询应只返回有效工作空间，并补齐未删除会话数量供前端排查归属。
     */
    @Test
    void pageForAdminReturnsWorkspaceRecordsWithConversationCounts() {
        WorkspaceDO workspace = new WorkspaceDO();
        workspace.setId(3001L);
        workspace.setName("本地项目");
        workspace.setRepositoryUrl("https://example.com/codingx.git");
        workspace.setBranchName("main");
        workspace.setWorkingDirectory("D:/code/CodingX");
        workspace.setRuntimeTarget(WorkspaceRepositoryImpl.RUNTIME_TARGET_LOCAL);
        workspace.setCreatedBy(1001L);
        workspace.setCreatedAt(LocalDateTime.of(2026, 5, 25, 0, 20, 0));
        workspace.setUpdatedAt(LocalDateTime.of(2026, 5, 25, 0, 21, 0));
        workspace.setDeleted(0);
        when(workspaceMapper.selectList(any())).thenReturn(List.of(workspace));
        when(chatConversationMapper.selectCount(any())).thenReturn(2L);

        AdminWorkspacePage page = workspaceRepository.pageForAdmin(
            new AdminWorkspaceQuery(1, 10, "codingx", "local")
        );

        assertEquals(1L, page.total());
        assertEquals(1L, page.current());
        assertEquals(1L, page.pages());
        assertEquals(1, page.records().size());
        assertEquals(3001L, page.records().get(0).id());
        assertEquals("本地项目", page.records().get(0).name());
        assertEquals("local", page.records().get(0).runtimeTarget());
        assertEquals(2L, page.records().get(0).conversationCount());
        verify(chatConversationMapper).selectCount(any());
    }
}
