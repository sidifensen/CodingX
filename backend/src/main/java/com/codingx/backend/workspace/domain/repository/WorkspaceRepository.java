package com.codingx.backend.workspace.domain.repository;

/**
 * Defines the repository contract exposed by WorkspaceRepository.
 */
public interface WorkspaceRepository {

    /**
     * Ensures the preconditions required by ensureExists.
     * @param workspaceId input argument.
     */
    void ensureExists(Long workspaceId);
}
