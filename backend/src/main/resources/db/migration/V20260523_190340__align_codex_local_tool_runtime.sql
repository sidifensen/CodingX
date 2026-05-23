-- 对齐 Codex 本地工具运行时：区分 Java 后端已真实适配的本地工具与暂未适配的 Codex session 工具。
COMMENT ON TABLE tool IS '聊天工具配置表';
COMMENT ON COLUMN tool.id IS '工具主键ID';
COMMENT ON COLUMN tool.tool_code IS '工具编码';
COMMENT ON COLUMN tool.display_name IS '工具名称';
COMMENT ON COLUMN tool.description IS '工具描述';
COMMENT ON COLUMN tool.category IS '工具分类';
COMMENT ON COLUMN tool.source_type IS '工具来源';
COMMENT ON COLUMN tool.enabled IS '是否启用 1启用 0禁用';
COMMENT ON COLUMN tool.sort_no IS '排序字段';
COMMENT ON COLUMN tool.created_at IS '创建时间';
COMMENT ON COLUMN tool.updated_at IS '更新时间';
COMMENT ON COLUMN tool.deleted IS '是否删除 0正常 1删除';

UPDATE tool
SET
    description = CASE tool_code
        WHEN 'shell_command' THEN '已适配：在当前本地工作区执行 PowerShell 或 Shell 命令，并返回退出码、输出与工作目录'
        WHEN 'exec_command' THEN '已适配：在当前本地工作区启动后台命令会话，供后续 write_stdin 写入'
        WHEN 'write_stdin' THEN '已适配：向 exec_command 创建的后台命令会话写入标准输入'
        WHEN 'apply_patch' THEN '已适配：在当前本地工作区应用 Codex Begin Patch 或标准 unified diff 补丁'
        WHEN 'update_plan' THEN '已适配：维护当前 Java 进程内的计划步骤状态'
        WHEN 'view_image' THEN '已适配：读取本地图片尺寸、大小与格式信息'
        WHEN 'tool_search' THEN '已适配：按关键词检索工具配置'
        WHEN 'test_sync_tool' THEN '已适配：用于验证工具调用链路的同步测试工具'
        ELSE description
    END,
    enabled = 1,
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0
WHERE tool_code IN (
    'shell_command',
    'exec_command',
    'write_stdin',
    'apply_patch',
    'update_plan',
    'view_image',
    'tool_search',
    'test_sync_tool'
);

UPDATE tool
SET
    description = display_name || ' 暂未适配真实 Codex 运行时，当前 Java 后端不会向模型暴露该工具',
    enabled = 0,
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0
WHERE tool_code IN (
    'list_mcp_resources',
    'list_mcp_resource_templates',
    'read_mcp_resource',
    'request_user_input',
    'spawn_agent',
    'send_input',
    'wait_agent',
    'close_agent',
    'resume_agent',
    'request_plugin_install',
    'request_permissions',
    'get_goal',
    'create_goal',
    'update_goal',
    'send_message',
    'followup_task',
    'list_agents',
    'spawn_agents_on_csv',
    'report_agent_job_result'
);
