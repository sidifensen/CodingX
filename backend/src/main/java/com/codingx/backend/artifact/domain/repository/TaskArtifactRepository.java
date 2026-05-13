package com.codingx.backend.artifact.domain.repository;

import com.codingx.backend.artifact.domain.model.TaskArtifact;
import java.util.List;

public interface TaskArtifactRepository {

    void save(TaskArtifact taskArtifact);

    List<TaskArtifact> findByTaskId(Long taskId);
}