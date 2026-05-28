-- 启用已由 Java 后端提供进程内实现的 Codex 运行时工具，修复历史环境沿用旧禁用状态导致接口拒绝调用。
UPDATE tool
SET
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0
WHERE tool_code IN (
    'update_plan',
    'spawn_agent',
    'send_input',
    'wait_agent',
    'close_agent',
    'resume_agent',
    'request_plugin_install',
    'request_permissions',
    'list_agents',
    'spawn_agents_on_csv',
    'report_agent_job_result'
);
