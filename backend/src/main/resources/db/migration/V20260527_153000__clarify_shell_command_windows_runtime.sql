-- 明确终端工具的 Windows PowerShell 运行边界，避免模型继续按 Bash 语法生成命令。
UPDATE tool
SET
    description = CASE tool_code
        WHEN 'shell_command' THEN '已适配：在当前本地工作区执行命令；Windows 环境使用 Windows PowerShell（powershell -NoProfile -Command），避免 mkdir -p、cat <<EOF、&& 和 < 等 Bash 写法，文件编辑优先使用 apply_patch 或 Set-Content'
        WHEN 'exec_command' THEN '已适配：在当前本地工作区启动后台命令会话；Windows 环境使用 Windows PowerShell，避免 Bash 专属语法，后续通过 write_stdin 写入交互输入'
        ELSE description
    END,
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0
WHERE tool_code IN ('shell_command', 'exec_command');
