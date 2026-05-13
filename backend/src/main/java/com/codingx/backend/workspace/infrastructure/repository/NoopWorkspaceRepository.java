package com.codingx.backend.workspace.infrastructure.repository;

import com.codingx.backend.workspace.domain.repository.WorkspaceRepository;
import org.springframework.stereotype.Repository;

@Repository
public class NoopWorkspaceRepository implements WorkspaceRepository {

    @Override
    public void ensureExists(Long workspaceId) {
        // Phase 2 keeps workspace minimal. Validation will be added when workspace CRUD is introduced.
    }
}