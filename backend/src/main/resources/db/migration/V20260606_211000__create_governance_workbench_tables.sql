CREATE TABLE IF NOT EXISTS governance_permission_policy (
    id BIGINT PRIMARY KEY,
    policy_code VARCHAR(128) NOT NULL UNIQUE,
    policy_name VARCHAR(128) NOT NULL,
    tool_code VARCHAR(128),
    command_pattern VARCHAR(512),
    path_pattern VARCHAR(512),
    action VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    description TEXT,
    enabled SMALLINT NOT NULL DEFAULT 1,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE governance_permission_policy IS '治理权限策略表';
COMMENT ON COLUMN governance_permission_policy.id IS '权限策略主键ID';
COMMENT ON COLUMN governance_permission_policy.policy_code IS '策略编码';
COMMENT ON COLUMN governance_permission_policy.policy_name IS '策略名称';
COMMENT ON COLUMN governance_permission_policy.tool_code IS '匹配工具编码';
COMMENT ON COLUMN governance_permission_policy.command_pattern IS '匹配命令片段';
COMMENT ON COLUMN governance_permission_policy.path_pattern IS '匹配路径片段';
COMMENT ON COLUMN governance_permission_policy.action IS '策略动作';
COMMENT ON COLUMN governance_permission_policy.risk_level IS '风险等级';
COMMENT ON COLUMN governance_permission_policy.description IS '策略说明';
COMMENT ON COLUMN governance_permission_policy.enabled IS '启用状态';
COMMENT ON COLUMN governance_permission_policy.sort_no IS '排序号';
COMMENT ON COLUMN governance_permission_policy.created_at IS '创建时间';
COMMENT ON COLUMN governance_permission_policy.updated_at IS '更新时间';
COMMENT ON COLUMN governance_permission_policy.deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS governance_permission_audit (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    conversation_id BIGINT,
    run_id BIGINT,
    tool_code VARCHAR(128) NOT NULL,
    tool_input TEXT,
    working_directory VARCHAR(1024),
    matched_policy_code VARCHAR(128),
    decision VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    result VARCHAR(64) NOT NULL,
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE governance_permission_audit IS '治理权限审计表';
COMMENT ON COLUMN governance_permission_audit.id IS '权限审计主键ID';
COMMENT ON COLUMN governance_permission_audit.user_id IS '触发用户ID';
COMMENT ON COLUMN governance_permission_audit.conversation_id IS '触发会话ID';
COMMENT ON COLUMN governance_permission_audit.run_id IS '触发运行ID';
COMMENT ON COLUMN governance_permission_audit.tool_code IS '工具编码';
COMMENT ON COLUMN governance_permission_audit.tool_input IS '工具输入文本';
COMMENT ON COLUMN governance_permission_audit.working_directory IS '工具工作目录';
COMMENT ON COLUMN governance_permission_audit.matched_policy_code IS '命中策略编码';
COMMENT ON COLUMN governance_permission_audit.decision IS '策略判定动作';
COMMENT ON COLUMN governance_permission_audit.risk_level IS '风险等级';
COMMENT ON COLUMN governance_permission_audit.result IS '处理结果';
COMMENT ON COLUMN governance_permission_audit.message IS '审计说明';
COMMENT ON COLUMN governance_permission_audit.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS governance_hook_rule (
    id BIGINT PRIMARY KEY,
    hook_code VARCHAR(128) NOT NULL UNIQUE,
    hook_name VARCHAR(128) NOT NULL,
    trigger_point VARCHAR(64) NOT NULL,
    condition_keyword VARCHAR(255),
    action_type VARCHAR(64) NOT NULL,
    action_config_json TEXT,
    enabled SMALLINT NOT NULL DEFAULT 1,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE governance_hook_rule IS '治理Hook规则表';
COMMENT ON COLUMN governance_hook_rule.id IS 'Hook规则主键ID';
COMMENT ON COLUMN governance_hook_rule.hook_code IS 'Hook编码';
COMMENT ON COLUMN governance_hook_rule.hook_name IS 'Hook名称';
COMMENT ON COLUMN governance_hook_rule.trigger_point IS 'Hook触发点';
COMMENT ON COLUMN governance_hook_rule.condition_keyword IS '条件关键字';
COMMENT ON COLUMN governance_hook_rule.action_type IS '动作类型';
COMMENT ON COLUMN governance_hook_rule.action_config_json IS '动作配置JSON';
COMMENT ON COLUMN governance_hook_rule.enabled IS '启用状态';
COMMENT ON COLUMN governance_hook_rule.sort_no IS '排序号';
COMMENT ON COLUMN governance_hook_rule.created_at IS '创建时间';
COMMENT ON COLUMN governance_hook_rule.updated_at IS '更新时间';
COMMENT ON COLUMN governance_hook_rule.deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS governance_hook_audit (
    id BIGINT PRIMARY KEY,
    hook_code VARCHAR(128) NOT NULL,
    trigger_point VARCHAR(64) NOT NULL,
    conversation_id BIGINT,
    run_id BIGINT,
    tool_code VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE governance_hook_audit IS '治理Hook审计表';
COMMENT ON COLUMN governance_hook_audit.id IS 'Hook审计主键ID';
COMMENT ON COLUMN governance_hook_audit.hook_code IS 'Hook编码';
COMMENT ON COLUMN governance_hook_audit.trigger_point IS 'Hook触发点';
COMMENT ON COLUMN governance_hook_audit.conversation_id IS '触发会话ID';
COMMENT ON COLUMN governance_hook_audit.run_id IS '触发运行ID';
COMMENT ON COLUMN governance_hook_audit.tool_code IS '工具编码';
COMMENT ON COLUMN governance_hook_audit.status IS '触发状态';
COMMENT ON COLUMN governance_hook_audit.message IS '审计说明';
COMMENT ON COLUMN governance_hook_audit.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS governance_project_profile (
    id BIGINT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    workspace_path VARCHAR(1024) NOT NULL,
    summary TEXT NOT NULL,
    tech_stack_json TEXT,
    entrypoints_json TEXT,
    verification_commands_json TEXT,
    status VARCHAR(32) NOT NULL,
    scanned_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE governance_project_profile IS '治理项目画像表';
COMMENT ON COLUMN governance_project_profile.id IS '项目画像主键ID';
COMMENT ON COLUMN governance_project_profile.workspace_id IS '工作空间ID';
COMMENT ON COLUMN governance_project_profile.workspace_path IS '工作空间路径';
COMMENT ON COLUMN governance_project_profile.summary IS '画像摘要';
COMMENT ON COLUMN governance_project_profile.tech_stack_json IS '技术栈JSON';
COMMENT ON COLUMN governance_project_profile.entrypoints_json IS '入口文件JSON';
COMMENT ON COLUMN governance_project_profile.verification_commands_json IS '验证命令JSON';
COMMENT ON COLUMN governance_project_profile.status IS '扫描状态';
COMMENT ON COLUMN governance_project_profile.scanned_at IS '扫描时间';
COMMENT ON COLUMN governance_project_profile.created_at IS '创建时间';
COMMENT ON COLUMN governance_project_profile.updated_at IS '更新时间';
COMMENT ON COLUMN governance_project_profile.deleted IS '逻辑删除标记';

CREATE TABLE IF NOT EXISTS governance_slash_command (
    id BIGINT PRIMARY KEY,
    command_code VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    command_type VARCHAR(64) NOT NULL,
    prompt_template TEXT NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE governance_slash_command IS '治理Slash命令表';
COMMENT ON COLUMN governance_slash_command.id IS 'Slash命令主键ID';
COMMENT ON COLUMN governance_slash_command.command_code IS '命令编码';
COMMENT ON COLUMN governance_slash_command.display_name IS '命令名称';
COMMENT ON COLUMN governance_slash_command.description IS '命令说明';
COMMENT ON COLUMN governance_slash_command.command_type IS '命令类型';
COMMENT ON COLUMN governance_slash_command.prompt_template IS '命令提示模板';
COMMENT ON COLUMN governance_slash_command.enabled IS '启用状态';
COMMENT ON COLUMN governance_slash_command.sort_no IS '排序号';
COMMENT ON COLUMN governance_slash_command.created_at IS '创建时间';
COMMENT ON COLUMN governance_slash_command.updated_at IS '更新时间';
COMMENT ON COLUMN governance_slash_command.deleted IS '逻辑删除标记';

CREATE INDEX IF NOT EXISTS idx_governance_permission_policy_enabled ON governance_permission_policy (enabled, sort_no ASC);
CREATE INDEX IF NOT EXISTS idx_governance_permission_audit_created ON governance_permission_audit (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_governance_hook_rule_trigger ON governance_hook_rule (trigger_point, enabled, sort_no ASC);
CREATE INDEX IF NOT EXISTS idx_governance_hook_audit_created ON governance_hook_audit (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_governance_project_profile_workspace ON governance_project_profile (workspace_id, scanned_at DESC);
CREATE INDEX IF NOT EXISTS idx_governance_slash_command_enabled ON governance_slash_command (enabled, sort_no ASC);

INSERT INTO governance_permission_policy (
    id, policy_code, policy_name, tool_code, command_pattern, path_pattern, action, risk_level, description, enabled, sort_no, deleted
)
VALUES
    (206060001, 'deny-dangerous-delete', '拒绝危险删除命令', null, 'rm -rf', null, 'DENY', 'HIGH', '拒绝高风险递归删除命令', 1, 1, 0),
    (206060002, 'confirm-git-push', '推送命令需要确认', null, 'git push', null, 'CONFIRM', 'MEDIUM', '远程推送需要用户确认后再执行', 1, 2, 0)
ON CONFLICT (policy_code) DO UPDATE
SET
    policy_name = EXCLUDED.policy_name,
    tool_code = EXCLUDED.tool_code,
    command_pattern = EXCLUDED.command_pattern,
    path_pattern = EXCLUDED.path_pattern,
    action = EXCLUDED.action,
    risk_level = EXCLUDED.risk_level,
    description = EXCLUDED.description,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;

INSERT INTO governance_slash_command (
    id, command_code, display_name, description, command_type, prompt_template, enabled, sort_no, deleted
)
VALUES
    (206060101, 'review', '/review', '审查当前改动并优先指出风险和测试缺口', 'BUILTIN', '请按代码评审方式检查当前改动，先列问题和风险，再给出简短总结。', 1, 1, 0),
    (206060102, 'fix-test', '/fix-test', '定位并修复测试失败', 'BUILTIN', '请先分析测试失败根因，再按 TDD 修复并重新验证。', 1, 2, 0),
    (206060103, 'commit', '/commit', '整理改动并按仓库规范提交', 'BUILTIN', '请检查工作区改动、运行必要验证，并使用中文规范提交信息提交本次改动。', 1, 3, 0),
    (206060104, 'generate-doc', '/generate-doc', '为当前功能生成或更新文档', 'BUILTIN', '请根据当前真实实现生成或更新功能文档，禁止写入尚未实现的规划能力。', 1, 4, 0)
ON CONFLICT (command_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    command_type = EXCLUDED.command_type,
    prompt_template = EXCLUDED.prompt_template,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;

INSERT INTO governance_hook_rule (
    id, hook_code, hook_name, trigger_point, condition_keyword, action_type, action_config_json, enabled, sort_no, deleted
)
VALUES
    (206060201, 'audit-before-tool', '工具调用前审计', 'BEFORE_TOOL_CALL', null, 'AUDIT', '{}', 1, 1, 0),
    (206060202, 'audit-task-complete', '任务完成审计', 'TASK_COMPLETED', null, 'AUDIT', '{}', 1, 2, 0)
ON CONFLICT (hook_code) DO UPDATE
SET
    hook_name = EXCLUDED.hook_name,
    trigger_point = EXCLUDED.trigger_point,
    condition_keyword = EXCLUDED.condition_keyword,
    action_type = EXCLUDED.action_type,
    action_config_json = EXCLUDED.action_config_json,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
