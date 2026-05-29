-- 暴露 OpenClaw 风格短工具名给模型，执行仍复用后端现有本地工具运行时边界。
INSERT INTO tool (id, tool_code, display_name, description, category, source_type, enabled, sort_no, deleted)
VALUES
    (9128, 'read', '读取文件', '读取当前本地工作区内的文本文件内容', '文件', 'codex-cli', 1, 1, 0),
    (9129, 'write', '写文件', '覆盖写入当前本地工作区内的文本文件，必要时自动创建父目录', '文件', 'codex-cli', 1, 2, 0),
    (9130, 'edit', '文本替换编辑', '基于唯一精确文本替换编辑当前本地工作区内的文件', '文件', 'codex-cli', 1, 3, 0),
    (9131, 'bash', '命令执行', '在当前本地工作区执行命令；Windows 环境实际使用 Windows PowerShell', '终端', 'codex-cli', 1, 4, 0),
    (9132, 'grep', '内容搜索', '在当前本地工作区内按正则搜索文本文件内容', '搜索', 'codex-cli', 1, 5, 0),
    (9133, 'find', '文件查找', '在当前本地工作区内按 glob 模式查找文件', '搜索', 'codex-cli', 1, 6, 0),
    (9134, 'ls', '列目录', '列出当前本地工作区内指定目录的文件和子目录', '文件', 'codex-cli', 1, 7, 0)
ON CONFLICT (tool_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
