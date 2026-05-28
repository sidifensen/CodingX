package com.codingx.task.infrastructure.persistence.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisMapperBuilderAssistant;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.codingx.task.domain.model.TaskArtifact;
import com.codingx.task.infrastructure.persistence.dataobject.TaskArtifactDO;
import com.codingx.task.infrastructure.persistence.mapper.TaskArtifactMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 验证任务产物仓储在读写时能够保留数据库中的持久化标识与时间字段。
 */
@ExtendWith(MockitoExtension.class)
class TaskArtifactRepositoryImplTest {

    /**
     * TaskArtifactMapper 依赖。
     */
    @Mock
    private TaskArtifactMapper taskArtifactMapper;

    /**
     * 被测仓储实现。
     */
    @InjectMocks
    private TaskArtifactRepositoryImpl taskArtifactRepository;

    /**
     * 读取任务产物时应还原数据库中的主键与创建时间，同时查询条件必须限定在当前 taskId。
     * @throws Exception 反射读取失败时抛出。
     */
    @SuppressWarnings("unchecked")
    @Test
    void findByTaskIdRestoresPersistedArtifactIdentity() throws Exception {
        TaskArtifactDO dataObject = new TaskArtifactDO();
        dataObject.setId(88L);
        dataObject.setTaskId(11L);
        dataObject.setArtifactType("summary");
        dataObject.setName("summary.txt");
        dataObject.setContent("done");
        dataObject.setStoragePath("artifacts/11/summary.txt");
        dataObject.setCreatedAt(LocalDateTime.of(2026, 5, 28, 10, 30, 0));
        when(taskArtifactMapper.selectList(any())).thenReturn(List.of(dataObject));

        TableInfoHelper.initTableInfo(new MybatisMapperBuilderAssistant(new MybatisConfiguration(), ""), TaskArtifactDO.class);
        List<TaskArtifact> artifacts = taskArtifactRepository.findByTaskId(11L);

        ArgumentCaptor<LambdaQueryWrapper<TaskArtifactDO>> captor =
            ArgumentCaptor.forClass((Class<LambdaQueryWrapper<TaskArtifactDO>>) (Class<?>) LambdaQueryWrapper.class);
        verify(taskArtifactMapper).selectList(captor.capture());
        LambdaQueryWrapper<TaskArtifactDO> queryWrapper = captor.getValue();

        assertEquals(1, artifacts.size());
        assertEquals(88L, artifacts.getFirst().getId());
        assertEquals(LocalDateTime.of(2026, 5, 28, 10, 30, 0), artifacts.getFirst().getCreatedAt());
        assertTrue(queryWrapper.getSqlSegment().contains("task_id"));
        assertEquals("ORDER BY created_at ASC", queryWrapper.getExpression().getOrderBy().getSqlSegment().trim());
        assertEquals(1, queryWrapper.getParamNameValuePairs().size());
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(11L));
    }
}
