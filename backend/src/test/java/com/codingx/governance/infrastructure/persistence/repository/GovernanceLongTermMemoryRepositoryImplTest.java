package com.codingx.governance.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codingx.governance.infrastructure.persistence.dataobject.GovernanceLongTermMemoryDO;
import com.codingx.governance.infrastructure.persistence.mapper.GovernanceLongTermMemoryMapper;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证长期记忆仓储的用户级/项目级范围过滤，避免跨工作空间串入项目约定。
 */
@ExtendWith(MockitoExtension.class)
class GovernanceLongTermMemoryRepositoryImplTest {

    /**
     * 纯 Mockito 单测不会启动 MyBatis，因此需要手动初始化实体表元数据，供 LambdaQueryWrapper 生成列名。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        TableInfoHelper.initTableInfo(
            new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""),
            GovernanceLongTermMemoryDO.class
        );
    }

    /** 长期记忆 Mapper 依赖，用于捕获实际查询条件。 */
    @Mock
    private GovernanceLongTermMemoryMapper mapper;

    /** 被测长期记忆仓储实现。 */
    @InjectMocks
    private GovernanceLongTermMemoryRepositoryImpl repository;

    /**
     * workspaceId 为空时只能查询 workspace_id 为空的用户级记忆，不能通过 OR 扩散到项目级记忆。
     */
    @Test
    @SuppressWarnings("unchecked")
    void findForUserWithoutWorkspaceShouldOnlyQueryUserScopeMemories() {
        when(mapper.selectList(any())).thenReturn(List.of());

        repository.findForUser(200L, null, "ACTIVE", 10);

        String sqlSegment = sqlSegment(captureSelectListWrapper());
        assertTrue(sqlSegment.contains("workspace_id is null"), sqlSegment);
        assertFalse(sqlSegment.contains(" or "), sqlSegment);
    }

    /**
     * workspaceId 存在时应同时查询用户级记忆和当前工作空间项目级记忆。
     */
    @Test
    @SuppressWarnings("unchecked")
    void findForUserWithWorkspaceShouldQueryUserAndCurrentProjectMemories() {
        when(mapper.selectList(any())).thenReturn(List.of());

        repository.findForUser(200L, 300L, "ACTIVE", 10);

        String sqlSegment = sqlSegment(captureSelectListWrapper());
        assertTrue(sqlSegment.contains("workspace_id is null"), sqlSegment);
        assertTrue(sqlSegment.contains(" or "), sqlSegment);
        assertTrue(sqlSegment.contains("workspace_id ="), sqlSegment);
    }

    /**
     * 已生效数量在当前工作空间下需要包含用户级记忆和当前项目记忆。
     */
    @Test
    @SuppressWarnings("unchecked")
    void countActiveByUserAndWorkspaceShouldQueryUserAndCurrentProjectMemories() {
        when(mapper.selectCount(any())).thenReturn(0L);

        repository.countActiveByUserAndWorkspace(200L, 300L);

        ArgumentCaptor<LambdaQueryWrapper<GovernanceLongTermMemoryDO>> captor =
            ArgumentCaptor.forClass((Class<LambdaQueryWrapper<GovernanceLongTermMemoryDO>>) (Class<?>) LambdaQueryWrapper.class);
        verify(mapper).selectCount(captor.capture());
        String sqlSegment = sqlSegment(captor.getValue());
        assertTrue(sqlSegment.contains("status ="), sqlSegment);
        assertTrue(sqlSegment.contains("workspace_id is null"), sqlSegment);
        assertTrue(sqlSegment.contains(" or "), sqlSegment);
        assertTrue(sqlSegment.contains("workspace_id ="), sqlSegment);
    }

    private LambdaQueryWrapper<GovernanceLongTermMemoryDO> captureSelectListWrapper() {
        ArgumentCaptor<LambdaQueryWrapper<GovernanceLongTermMemoryDO>> captor =
            ArgumentCaptor.forClass((Class<LambdaQueryWrapper<GovernanceLongTermMemoryDO>>) (Class<?>) LambdaQueryWrapper.class);
        verify(mapper).selectList(captor.capture());
        return captor.getValue();
    }

    private String sqlSegment(LambdaQueryWrapper<GovernanceLongTermMemoryDO> wrapper) {
        return wrapper.getCustomSqlSegment().toLowerCase(Locale.ROOT);
    }
}
