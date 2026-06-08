CREATE TABLE IF NOT EXISTS automation_task (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    workspace_id BIGINT,
    source_type VARCHAR(32) NOT NULL,
    source_conversation_id BIGINT,
    name VARCHAR(80) NOT NULL,
    prompt TEXT NOT NULL,
    schedule_type VARCHAR(32) NOT NULL,
    schedule_time VARCHAR(5),
    schedule_day_of_week SMALLINT,
    once_execute_at TIMESTAMP,
    next_run_at TIMESTAMP,
    last_run_at TIMESTAMP,
    last_run_status VARCHAR(32),
    enabled SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_automation_task_user_next_run
    ON automation_task (user_id, deleted, enabled, next_run_at);

CREATE INDEX IF NOT EXISTS idx_automation_task_due
    ON automation_task (deleted, enabled, next_run_at);

COMMENT ON TABLE automation_task IS '自动化定时任务表';
COMMENT ON COLUMN automation_task.id IS '任务主键ID';
COMMENT ON COLUMN automation_task.user_id IS '任务归属用户ID';
COMMENT ON COLUMN automation_task.workspace_id IS '任务归属工作空间ID';
COMMENT ON COLUMN automation_task.source_type IS '创建来源，MANUAL手动创建，CHAT会话创建';
COMMENT ON COLUMN automation_task.source_conversation_id IS '来源会话ID';
COMMENT ON COLUMN automation_task.name IS '任务名称';
COMMENT ON COLUMN automation_task.prompt IS '任务需求说明';
COMMENT ON COLUMN automation_task.schedule_type IS '计划类型，DAILY每日，WEEKLY每周，ONCE一次性';
COMMENT ON COLUMN automation_task.schedule_time IS '固定执行时间，格式HH:mm';
COMMENT ON COLUMN automation_task.schedule_day_of_week IS '每周执行星期，1周一至7周日';
COMMENT ON COLUMN automation_task.once_execute_at IS '一次性执行时间';
COMMENT ON COLUMN automation_task.next_run_at IS '下一次计划触发时间';
COMMENT ON COLUMN automation_task.last_run_at IS '最近一次触发时间';
COMMENT ON COLUMN automation_task.last_run_status IS '最近一次触发状态';
COMMENT ON COLUMN automation_task.enabled IS '启用状态，0停用，1启用';
COMMENT ON COLUMN automation_task.created_at IS '记录创建时间';
COMMENT ON COLUMN automation_task.updated_at IS '记录最后更新时间';
COMMENT ON COLUMN automation_task.deleted IS '逻辑删除标记，0表示未删除，1表示已删除';
