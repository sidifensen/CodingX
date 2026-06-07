ALTER TABLE governance_project_profile
    ADD COLUMN IF NOT EXISTS module_map_json TEXT,
    ADD COLUMN IF NOT EXISTS test_commands_json TEXT,
    ADD COLUMN IF NOT EXISTS key_entrypoints_json TEXT,
    ADD COLUMN IF NOT EXISTS risk_points_json TEXT,
    ADD COLUMN IF NOT EXISTS agent_context TEXT;

COMMENT ON COLUMN governance_project_profile.module_map_json IS '模块地图JSON';
COMMENT ON COLUMN governance_project_profile.test_commands_json IS '测试命令JSON';
COMMENT ON COLUMN governance_project_profile.key_entrypoints_json IS '关键入口JSON';
COMMENT ON COLUMN governance_project_profile.risk_points_json IS '风险点JSON';
COMMENT ON COLUMN governance_project_profile.agent_context IS 'Agent输入上下文';

CREATE TABLE IF NOT EXISTS governance_long_term_memory (
    id BIGINT PRIMARY KEY,
    memory_scope VARCHAR(32) NOT NULL,
    user_id BIGINT,
    workspace_id BIGINT,
    memory_key VARCHAR(256) NOT NULL UNIQUE,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    source_type VARCHAR(64),
    source_conversation_id BIGINT,
    source_message_id BIGINT,
    keyword_json TEXT,
    confidence_score NUMERIC(6, 4),
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE governance_long_term_memory IS '治理长期记忆表';
COMMENT ON COLUMN governance_long_term_memory.id IS '长期记忆主键ID';
COMMENT ON COLUMN governance_long_term_memory.memory_scope IS '记忆范围';
COMMENT ON COLUMN governance_long_term_memory.user_id IS '用户ID';
COMMENT ON COLUMN governance_long_term_memory.workspace_id IS '工作空间ID';
COMMENT ON COLUMN governance_long_term_memory.memory_key IS '记忆去重键';
COMMENT ON COLUMN governance_long_term_memory.content IS '记忆内容';
COMMENT ON COLUMN governance_long_term_memory.status IS '记忆状态';
COMMENT ON COLUMN governance_long_term_memory.source_type IS '来源类型';
COMMENT ON COLUMN governance_long_term_memory.source_conversation_id IS '来源会话ID';
COMMENT ON COLUMN governance_long_term_memory.source_message_id IS '来源消息ID';
COMMENT ON COLUMN governance_long_term_memory.keyword_json IS '关键词JSON';
COMMENT ON COLUMN governance_long_term_memory.confidence_score IS '置信分数';
COMMENT ON COLUMN governance_long_term_memory.last_used_at IS '最近使用时间';
COMMENT ON COLUMN governance_long_term_memory.created_at IS '创建时间';
COMMENT ON COLUMN governance_long_term_memory.updated_at IS '更新时间';
COMMENT ON COLUMN governance_long_term_memory.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_governance_long_term_memory_user ON governance_long_term_memory (user_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_governance_long_term_memory_workspace ON governance_long_term_memory (workspace_id, status, updated_at DESC);
