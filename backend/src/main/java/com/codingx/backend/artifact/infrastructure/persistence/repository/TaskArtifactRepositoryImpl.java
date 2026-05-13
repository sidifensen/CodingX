package com.codingx.backend.artifact.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.codingx.backend.artifact.domain.model.TaskArtifact;
import com.codingx.backend.artifact.domain.repository.TaskArtifactRepository;
import com.codingx.backend.artifact.infrastructure.persistence.dataobject.TaskArtifactDO;
import com.codingx.backend.artifact.infrastructure.persistence.mapper.TaskArtifactMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TaskArtifactRepositoryImpl implements TaskArtifactRepository {

    private final TaskArtifactMapper taskArtifactMapper;

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