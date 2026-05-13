package com.codingx.backend.workspace.domain.repository;

public interface WorkspaceRepository {

    void ensureExists(Long workspaceId);
}