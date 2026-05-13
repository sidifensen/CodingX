package com.codingx.backend.workspace.infrastructure.repository;
import com.codingx.backend.workspace.domain.repository.WorkspaceRepository;
import org.springframework.stereotype.Repository;

/**
 * Defines the responsibilities handled by NoopWorkspaceRepository.
 */
@Repository
public class NoopWorkspaceRepository implements WorkspaceRepository {

    /**
     * Ensures the preconditions required by ensureExists.
     * @param workspaceId input argument.
     */
    @Override
    public void ensureExists(Long workspaceId) {
    }
}
