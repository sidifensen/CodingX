CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cx_workspace (
    id BIGINT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    repository_url VARCHAR(512),
    branch_name VARCHAR(128),
    working_directory VARCHAR(512),
    runtime_target VARCHAR(32) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cx_task (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    runtime_type VARCHAR(32) NOT NULL,
    workspace_id BIGINT,
    created_by BIGINT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    error_message TEXT,
    summary TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cx_task_event (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    sequence_no BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cx_task_artifact (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    content TEXT,
    storage_path VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cx_chat_conversation (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    created_by BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_message_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cx_chat_message (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider VARCHAR(64),
    model VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cx_task_created_by ON cx_task (created_by, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cx_task_status ON cx_task (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cx_task_event_task_seq ON cx_task_event (task_id, sequence_no ASC);
CREATE INDEX IF NOT EXISTS idx_cx_task_artifact_task ON cx_task_artifact (task_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_cx_chat_conversation_user ON cx_chat_conversation (created_by, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_cx_chat_message_conversation ON cx_chat_message (conversation_id, created_at ASC);
