CREATE TABLE IF NOT EXISTS chat_goal (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    goal_key VARCHAR(128) NOT NULL DEFAULT 'default',
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    progress_summary TEXT,
    created_run_id BIGINT,
    updated_run_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_goal IS '聊天目标表';
COMMENT ON COLUMN chat_goal.id IS '目标主键ID';
COMMENT ON COLUMN chat_goal.conversation_id IS '所属会话ID';
COMMENT ON COLUMN chat_goal.user_id IS '目标归属用户ID';
COMMENT ON COLUMN chat_goal.goal_key IS '目标稳定键';
COMMENT ON COLUMN chat_goal.title IS '目标标题';
COMMENT ON COLUMN chat_goal.description IS '目标说明';
COMMENT ON COLUMN chat_goal.status IS '目标状态';
COMMENT ON COLUMN chat_goal.progress_summary IS '目标进度摘要';
COMMENT ON COLUMN chat_goal.created_run_id IS '创建目标运行ID';
COMMENT ON COLUMN chat_goal.updated_run_id IS '最近更新运行ID';
COMMENT ON COLUMN chat_goal.created_at IS '创建时间';
COMMENT ON COLUMN chat_goal.updated_at IS '更新时间';
COMMENT ON COLUMN chat_goal.completed_at IS '完成时间';
COMMENT ON COLUMN chat_goal.deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS chat_goal_step (
    id BIGINT PRIMARY KEY,
    goal_id BIGINT NOT NULL,
    step_key VARCHAR(128) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    sort_no INTEGER NOT NULL DEFAULT 0,
    detail TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_goal_step IS '聊天目标步骤表';
COMMENT ON COLUMN chat_goal_step.id IS '步骤主键ID';
COMMENT ON COLUMN chat_goal_step.goal_id IS '所属目标ID';
COMMENT ON COLUMN chat_goal_step.step_key IS '步骤稳定键';
COMMENT ON COLUMN chat_goal_step.title IS '步骤标题';
COMMENT ON COLUMN chat_goal_step.status IS '步骤状态';
COMMENT ON COLUMN chat_goal_step.sort_no IS '步骤排序号';
COMMENT ON COLUMN chat_goal_step.detail IS '步骤详情';
COMMENT ON COLUMN chat_goal_step.started_at IS '开始时间';
COMMENT ON COLUMN chat_goal_step.completed_at IS '完成时间';
COMMENT ON COLUMN chat_goal_step.updated_at IS '更新时间';
COMMENT ON COLUMN chat_goal_step.deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS chat_goal_event (
    id BIGINT PRIMARY KEY,
    goal_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    run_id BIGINT,
    event_type VARCHAR(64) NOT NULL,
    payload_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_goal_event IS '聊天目标事件表';
COMMENT ON COLUMN chat_goal_event.id IS '事件主键ID';
COMMENT ON COLUMN chat_goal_event.goal_id IS '所属目标ID';
COMMENT ON COLUMN chat_goal_event.conversation_id IS '所属会话ID';
COMMENT ON COLUMN chat_goal_event.run_id IS '触发运行ID';
COMMENT ON COLUMN chat_goal_event.event_type IS '目标事件类型';
COMMENT ON COLUMN chat_goal_event.payload_json IS '事件载荷JSON';
COMMENT ON COLUMN chat_goal_event.created_at IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_chat_goal_conversation_status ON chat_goal (conversation_id, user_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_chat_goal_key_conversation ON chat_goal (conversation_id, user_id, goal_key, deleted);
CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_goal_active_conversation_user ON chat_goal (conversation_id, user_id) WHERE status = 'ACTIVE' AND deleted = 0;
CREATE INDEX IF NOT EXISTS idx_chat_goal_step_goal_sort ON chat_goal_step (goal_id, sort_no ASC, deleted);
CREATE INDEX IF NOT EXISTS idx_chat_goal_event_conversation ON chat_goal_event (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_goal_event_goal ON chat_goal_event (goal_id, created_at DESC);
