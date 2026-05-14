package com.codingx.artifact.infrastructure.persistence.repository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.artifact.domain.model.TaskArtifact;
import com.codingx.artifact.domain.repository.TaskArtifactRepository;
import com.codingx.artifact.infrastructure.persistence.dataobject.TaskArtifactDO;
import com.codingx.artifact.infrastructure.persistence.mapper.TaskArtifactMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 实现 TaskArtifactRepositoryImpl 的持久化行为。
 */
@Repository
@RequiredArgsConstructor
public class TaskArtifactRepositoryImpl implements TaskArtifactRepository {

    /**
     * TaskArtifactMapper 依赖。
     */
    private final TaskArtifactMapper taskArtifactMapper;

    /**
     * 持久化 save 处理的状态。
     * @param taskArtifact 输入参数。
     */
    @Override
    public void save(TaskArtifact taskArtifact) {
        TaskArtifactDO dataObject = new TaskArtifactDO();
        dataObject.setId(taskArtifact.getId());
        dataObject.setTaskId(taskArtifact.getTaskId());
        dataObject.setArtifactType(taskArtifact.getArtifactType());
        dataObject.setName(taskArtifact.getName());
        dataObject.setContent(taskArtifact.getContent());
        dataObject.setStoragePath(taskArtifact.getStoragePath());
        dataObject.setCreatedAt(taskArtifact.getCreatedAt());
        taskArtifactMapper.insert(dataObject);
    }

    /**
     * 查询 findByTaskId 需要的数据。
     * @param taskId 输入参数。
     * @return 输入参数。
     */
    @Override
    public List<TaskArtifact> findByTaskId(Long taskId) {
        return taskArtifactMapper.selectList(new LambdaQueryWrapper<TaskArtifactDO>()
                .eq(TaskArtifactDO::getTaskId, taskId)
                .orderByAsc(TaskArtifactDO::getCreatedAt))
            .stream()
            .map(dataObject -> TaskArtifact.create(dataObject.getTaskId(), dataObject.getArtifactType(), dataObject.getName(), dataObject.getContent(), dataObject.getStoragePath()))
            .toList();
    }
}
