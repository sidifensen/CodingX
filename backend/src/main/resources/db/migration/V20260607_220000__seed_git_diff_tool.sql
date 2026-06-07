-- 为用户端代码审查侧栏启用只读 git diff 工具；该工具只读取差异，不执行写入。
INSERT INTO tool (id, tool_code, display_name, description, category, source_type, enabled, sort_no, deleted)
VALUES
    (9135, 'git_diff', 'Git 差异读取', '读取当前本地工作区的未暂存、已暂存、提交或分支差异', '代码审查', 'codex-cli', 1, 28, 0)
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
