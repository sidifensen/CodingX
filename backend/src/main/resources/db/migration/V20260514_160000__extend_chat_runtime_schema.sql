ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS last_run_id BIGINT;
COMMENT ON COLUMN chat_conversation.last_run_id IS '最近一次执行记录 ID';

ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS run_id BIGINT,
    ADD COLUMN IF NOT EXISTS thinking_content TEXT,
    ADD COLUMN IF NOT EXISTS thinking_duration INTEGER,
    ADD COLUMN IF NOT EXISTS intent_code VARCHAR(128);
COMMENT ON COLUMN chat_message.run_id IS '所属执行记录 ID';
COMMENT ON COLUMN chat_message.thinking_content IS '深度思考内容';
COMMENT ON COLUMN chat_message.thinking_duration IS '深度思考耗时（秒）';
COMMENT ON COLUMN chat_message.intent_code IS '命中的意图编码';

CREATE TABLE IF NOT EXISTS chat_conversation_summary (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_message_id BIGINT,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_conversation_summary IS '会话摘要表（与消息表分离存储）';
COMMENT ON COLUMN chat_conversation_summary.id IS '主键ID';
COMMENT ON COLUMN chat_conversation_summary.conversation_id IS '会话ID';
COMMENT ON COLUMN chat_conversation_summary.user_id IS '用户ID';
COMMENT ON COLUMN chat_conversation_summary.last_message_id IS '摘要最后消息ID';
COMMENT ON COLUMN chat_conversation_summary.content IS '会话摘要内容';
COMMENT ON COLUMN chat_conversation_summary.created_at IS '创建时间';
COMMENT ON COLUMN chat_conversation_summary.updated_at IS '更新时间';
COMMENT ON COLUMN chat_conversation_summary.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE IF NOT EXISTS chat_message_feedback (
    id BIGINT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vote INTEGER NOT NULL,
    reason VARCHAR(255),
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_message_feedback IS '会话消息反馈表';
COMMENT ON COLUMN chat_message_feedback.id IS '主键ID';
COMMENT ON COLUMN chat_message_feedback.message_id IS '消息ID';
COMMENT ON COLUMN chat_message_feedback.conversation_id IS '会话ID';
COMMENT ON COLUMN chat_message_feedback.user_id IS '用户ID';
COMMENT ON COLUMN chat_message_feedback.vote IS '投票 1：赞 -1：踩';
COMMENT ON COLUMN chat_message_feedback.reason IS '反馈原因';
COMMENT ON COLUMN chat_message_feedback.comment IS '反馈评论';
COMMENT ON COLUMN chat_message_feedback.created_at IS '创建时间';
COMMENT ON COLUMN chat_message_feedback.updated_at IS '更新时间';
COMMENT ON COLUMN chat_message_feedback.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE IF NOT EXISTS chat_execution_run (
    id BIGINT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    request_message_id BIGINT,
    response_message_id BIGINT,
    task_id BIGINT NOT NULL,
    intent_code VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    queue_status VARCHAR(32),
    search_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    artifact_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_execution_run IS '聊天执行记录表，记录一次用户提问对应的完整执行过程';
COMMENT ON COLUMN chat_execution_run.id IS '执行记录主键 ID';
COMMENT ON COLUMN chat_execution_run.conversation_id IS '所属会话 ID';
COMMENT ON COLUMN chat_execution_run.request_message_id IS '请求消息 ID';
COMMENT ON COLUMN chat_execution_run.response_message_id IS '响应消息 ID';
COMMENT ON COLUMN chat_execution_run.task_id IS '任务 ID';
COMMENT ON COLUMN chat_execution_run.intent_code IS '命中的意图编码';
COMMENT ON COLUMN chat_execution_run.status IS '执行状态';
COMMENT ON COLUMN chat_execution_run.queue_status IS '排队状态';
COMMENT ON COLUMN chat_execution_run.search_enabled IS '是否启用联网搜索';
COMMENT ON COLUMN chat_execution_run.artifact_enabled IS '是否启用产物生成';
COMMENT ON COLUMN chat_execution_run.error_message IS '执行失败时的错误信息';
COMMENT ON COLUMN chat_execution_run.started_at IS '开始时间';
COMMENT ON COLUMN chat_execution_run.finished_at IS '结束时间';
COMMENT ON COLUMN chat_execution_run.created_at IS '创建时间';
COMMENT ON COLUMN chat_execution_run.updated_at IS '更新时间';

CREATE TABLE IF NOT EXISTS chat_execution_step (
    id BIGINT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    step_title VARCHAR(255) NOT NULL,
    step_status VARCHAR(32) NOT NULL,
    sequence_no BIGINT NOT NULL,
    content TEXT,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_execution_step IS '聊天执行步骤表，记录面向用户可读的过程步骤与状态';
COMMENT ON COLUMN chat_execution_step.id IS '执行步骤主键 ID';
COMMENT ON COLUMN chat_execution_step.run_id IS '所属执行记录 ID';
COMMENT ON COLUMN chat_execution_step.step_type IS '步骤类型';
COMMENT ON COLUMN chat_execution_step.step_title IS '步骤标题';
COMMENT ON COLUMN chat_execution_step.step_status IS '步骤状态';
COMMENT ON COLUMN chat_execution_step.sequence_no IS '步骤顺序号';
COMMENT ON COLUMN chat_execution_step.content IS '步骤详细内容';
COMMENT ON COLUMN chat_execution_step.metadata_json IS '步骤附加元数据 JSON 文本';
COMMENT ON COLUMN chat_execution_step.created_at IS '创建时间';
COMMENT ON COLUMN chat_execution_step.updated_at IS '更新时间';

CREATE TABLE IF NOT EXISTS chat_message_reference (
    id BIGINT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    message_id BIGINT,
    conversation_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    title VARCHAR(512) NOT NULL,
    url VARCHAR(2048),
    site_name VARCHAR(255),
    snippet TEXT,
    rank_no INTEGER,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_message_reference IS '会话参考来源表，记录最终展示给用户的搜索来源与引用片段';
COMMENT ON COLUMN chat_message_reference.id IS '参考来源主键 ID';
COMMENT ON COLUMN chat_message_reference.run_id IS '所属执行记录 ID';
COMMENT ON COLUMN chat_message_reference.message_id IS '关联消息 ID';
COMMENT ON COLUMN chat_message_reference.conversation_id IS '所属会话 ID';
COMMENT ON COLUMN chat_message_reference.source_type IS '来源类型';
COMMENT ON COLUMN chat_message_reference.title IS '来源标题';
COMMENT ON COLUMN chat_message_reference.url IS '来源链接';
COMMENT ON COLUMN chat_message_reference.site_name IS '来源站点名称';
COMMENT ON COLUMN chat_message_reference.snippet IS '引用摘要片段';
COMMENT ON COLUMN chat_message_reference.rank_no IS '展示顺序';
COMMENT ON COLUMN chat_message_reference.metadata_json IS '来源附加元数据 JSON 文本';
COMMENT ON COLUMN chat_message_reference.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS chat_message_artifact (
    id BIGINT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    message_id BIGINT,
    conversation_id BIGINT NOT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128),
    storage_path VARCHAR(1024) NOT NULL,
    content_preview TEXT,
    metadata_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_message_artifact IS '会话产物表，记录聊天过程中生成的文档与文件产物';
COMMENT ON COLUMN chat_message_artifact.id IS '产物主键 ID';
COMMENT ON COLUMN chat_message_artifact.run_id IS '所属执行记录 ID';
COMMENT ON COLUMN chat_message_artifact.message_id IS '关联消息 ID';
COMMENT ON COLUMN chat_message_artifact.conversation_id IS '所属会话 ID';
COMMENT ON COLUMN chat_message_artifact.artifact_type IS '产物类型';
COMMENT ON COLUMN chat_message_artifact.name IS '产物名称';
COMMENT ON COLUMN chat_message_artifact.mime_type IS '产物 MIME 类型';
COMMENT ON COLUMN chat_message_artifact.storage_path IS '产物存储路径';
COMMENT ON COLUMN chat_message_artifact.content_preview IS '产物预览内容';
COMMENT ON COLUMN chat_message_artifact.metadata_json IS '产物附加元数据 JSON 文本';
COMMENT ON COLUMN chat_message_artifact.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS chat_intent_node (
    id BIGINT PRIMARY KEY,
    intent_code VARCHAR(128) NOT NULL UNIQUE,
    parent_code VARCHAR(128),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    intent_type VARCHAR(32) NOT NULL,
    prompt_template TEXT,
    enabled SMALLINT NOT NULL DEFAULT 1,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_intent_node IS '意图树节点配置表';
COMMENT ON COLUMN chat_intent_node.id IS '主键ID';
COMMENT ON COLUMN chat_intent_node.intent_code IS '业务唯一标识';
COMMENT ON COLUMN chat_intent_node.parent_code IS '父节点标识';
COMMENT ON COLUMN chat_intent_node.name IS '展示名称';
COMMENT ON COLUMN chat_intent_node.description IS '语义描述';
COMMENT ON COLUMN chat_intent_node.intent_type IS '意图类型';
COMMENT ON COLUMN chat_intent_node.prompt_template IS '提示词模板';
COMMENT ON COLUMN chat_intent_node.enabled IS '是否启用 1：启用 0：禁用';
COMMENT ON COLUMN chat_intent_node.sort_no IS '排序字段';
COMMENT ON COLUMN chat_intent_node.created_at IS '创建时间';
COMMENT ON COLUMN chat_intent_node.updated_at IS '更新时间';
COMMENT ON COLUMN chat_intent_node.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE IF NOT EXISTS chat_intent_example (
    id BIGINT PRIMARY KEY,
    intent_code VARCHAR(128) NOT NULL,
    example_text TEXT NOT NULL,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_intent_example IS '意图示例语句表';
COMMENT ON COLUMN chat_intent_example.id IS '主键ID';
COMMENT ON COLUMN chat_intent_example.intent_code IS '关联意图编码';
COMMENT ON COLUMN chat_intent_example.example_text IS '示例问题内容';
COMMENT ON COLUMN chat_intent_example.sort_no IS '排序字段';
COMMENT ON COLUMN chat_intent_example.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS chat_trace_run (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL UNIQUE,
    trace_name VARCHAR(255) NOT NULL,
    entry_method VARCHAR(255),
    conversation_id BIGINT,
    task_id BIGINT,
    user_id BIGINT,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    duration_ms BIGINT,
    extra_data_json TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_trace_run IS 'Trace 运行记录表';
COMMENT ON COLUMN chat_trace_run.id IS 'ID';
COMMENT ON COLUMN chat_trace_run.trace_id IS '全局链路ID';
COMMENT ON COLUMN chat_trace_run.trace_name IS '链路名称';
COMMENT ON COLUMN chat_trace_run.entry_method IS '入口方法';
COMMENT ON COLUMN chat_trace_run.conversation_id IS '会话ID';
COMMENT ON COLUMN chat_trace_run.task_id IS '任务ID';
COMMENT ON COLUMN chat_trace_run.user_id IS '用户ID';
COMMENT ON COLUMN chat_trace_run.status IS 'RUNNING/SUCCESS/ERROR';
COMMENT ON COLUMN chat_trace_run.error_message IS '错误信息';
COMMENT ON COLUMN chat_trace_run.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN chat_trace_run.extra_data_json IS '扩展字段(JSON)';
COMMENT ON COLUMN chat_trace_run.started_at IS '开始时间';
COMMENT ON COLUMN chat_trace_run.finished_at IS '结束时间';
COMMENT ON COLUMN chat_trace_run.created_at IS '创建时间';
COMMENT ON COLUMN chat_trace_run.updated_at IS '更新时间';
COMMENT ON COLUMN chat_trace_run.deleted IS '是否删除';

CREATE TABLE IF NOT EXISTS chat_trace_node (
    id BIGINT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL,
    node_id VARCHAR(64) NOT NULL,
    parent_node_id VARCHAR(64),
    depth INTEGER NOT NULL DEFAULT 0,
    node_type VARCHAR(64) NOT NULL,
    node_name VARCHAR(255) NOT NULL,
    class_name VARCHAR(512),
    method_name VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    duration_ms BIGINT,
    extra_data_json TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE chat_trace_node IS 'Trace 节点记录表';
COMMENT ON COLUMN chat_trace_node.id IS 'ID';
COMMENT ON COLUMN chat_trace_node.trace_id IS '所属链路ID';
COMMENT ON COLUMN chat_trace_node.node_id IS '节点ID';
COMMENT ON COLUMN chat_trace_node.parent_node_id IS '父节点ID';
COMMENT ON COLUMN chat_trace_node.depth IS '节点深度';
COMMENT ON COLUMN chat_trace_node.node_type IS '节点类型';
COMMENT ON COLUMN chat_trace_node.node_name IS '节点名称';
COMMENT ON COLUMN chat_trace_node.class_name IS '类名';
COMMENT ON COLUMN chat_trace_node.method_name IS '方法名';
COMMENT ON COLUMN chat_trace_node.status IS 'RUNNING/SUCCESS/ERROR';
COMMENT ON COLUMN chat_trace_node.error_message IS '错误信息';
COMMENT ON COLUMN chat_trace_node.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN chat_trace_node.extra_data_json IS '扩展字段(JSON)';
COMMENT ON COLUMN chat_trace_node.started_at IS '开始时间';
COMMENT ON COLUMN chat_trace_node.finished_at IS '结束时间';
COMMENT ON COLUMN chat_trace_node.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS chat_query_term_mapping (
    id BIGINT PRIMARY KEY,
    source_term VARCHAR(255) NOT NULL,
    target_term VARCHAR(255) NOT NULL,
    mapping_type VARCHAR(32) NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_query_term_mapping IS '关键词归一化映射表';
COMMENT ON COLUMN chat_query_term_mapping.id IS 'ID';
COMMENT ON COLUMN chat_query_term_mapping.source_term IS '源词';
COMMENT ON COLUMN chat_query_term_mapping.target_term IS '目标词';
COMMENT ON COLUMN chat_query_term_mapping.mapping_type IS '映射类型';
COMMENT ON COLUMN chat_query_term_mapping.enabled IS '是否启用';
COMMENT ON COLUMN chat_query_term_mapping.sort_no IS '优先级排序';
COMMENT ON COLUMN chat_query_term_mapping.created_at IS '创建时间';
COMMENT ON COLUMN chat_query_term_mapping.updated_at IS '更新时间';
COMMENT ON COLUMN chat_query_term_mapping.deleted IS '是否删除 0：正常 1：删除';

CREATE TABLE IF NOT EXISTS chat_sample_question (
    id BIGINT PRIMARY KEY,
    question_text VARCHAR(512) NOT NULL,
    category VARCHAR(128),
    enabled SMALLINT NOT NULL DEFAULT 1,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_sample_question IS '示例问题表';
COMMENT ON COLUMN chat_sample_question.id IS 'ID';
COMMENT ON COLUMN chat_sample_question.question_text IS '示例问题内容';
COMMENT ON COLUMN chat_sample_question.category IS '分类';
COMMENT ON COLUMN chat_sample_question.enabled IS '是否启用';
COMMENT ON COLUMN chat_sample_question.sort_no IS '排序字段';
COMMENT ON COLUMN chat_sample_question.created_at IS '创建时间';
COMMENT ON COLUMN chat_sample_question.updated_at IS '更新时间';
COMMENT ON COLUMN chat_sample_question.deleted IS '是否删除 0：正常 1：删除';

CREATE INDEX IF NOT EXISTS idx_chat_conversation_summary_conv_user ON chat_conversation_summary (conversation_id, user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_feedback_message_user ON chat_message_feedback (message_id, user_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_feedback_conversation ON chat_message_feedback (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_execution_run_conversation ON chat_execution_run (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_execution_run_task ON chat_execution_run (task_id);
CREATE INDEX IF NOT EXISTS idx_chat_execution_step_run_seq ON chat_execution_step (run_id, sequence_no ASC);
CREATE INDEX IF NOT EXISTS idx_chat_message_reference_run ON chat_message_reference (run_id, rank_no ASC);
CREATE INDEX IF NOT EXISTS idx_chat_message_reference_conversation ON chat_message_reference (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_message_artifact_run ON chat_message_artifact (run_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_chat_message_artifact_conversation ON chat_message_artifact (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_intent_node_parent ON chat_intent_node (parent_code, sort_no ASC);
CREATE INDEX IF NOT EXISTS idx_chat_intent_example_code ON chat_intent_example (intent_code, sort_no ASC);
CREATE INDEX IF NOT EXISTS idx_chat_trace_run_conversation ON chat_trace_run (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_trace_run_task ON chat_trace_run (task_id);
CREATE INDEX IF NOT EXISTS idx_chat_trace_node_trace_depth ON chat_trace_node (trace_id, depth ASC, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_chat_query_term_mapping_source ON chat_query_term_mapping (source_term, enabled, sort_no ASC);
CREATE INDEX IF NOT EXISTS idx_chat_sample_question_enabled ON chat_sample_question (enabled, sort_no ASC);
