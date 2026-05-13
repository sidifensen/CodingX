package com.codingx.backend.artifact.domain.repository;
import com.codingx.backend.artifact.domain.model.TaskArtifact;
import java.util.List;

/**
 * Defines the repository contract exposed by TaskArtifactRepository.
 */
public interface TaskArtifactRepository {

    /**
     * Persists the state handled by save.
     * @param taskArtifact input argument.
     */
    void save(TaskArtifact taskArtifact);

    /**
     * Finds the data required by findByTaskId.
     * @param taskId input argument.
     * @return processing result.
     */
    List<TaskArtifact> findByTaskId(Long taskId);
}
