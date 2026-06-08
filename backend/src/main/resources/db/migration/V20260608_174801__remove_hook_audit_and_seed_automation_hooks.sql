DROP TABLE IF EXISTS governance_hook_audit;

DELETE FROM governance_hook_rule
WHERE hook_code IN ('audit-before-tool', 'audit-task-complete');

INSERT INTO governance_hook_rule (
    id, hook_code, hook_name, trigger_point, condition_keyword, action_type, action_config_json, enabled, sort_no, deleted
)
VALUES
    (206060201, 'before-task-start', '任务开始前通知', 'BEFORE_TASK_START', null, 'DESKTOP_NOTIFY', '{"title":"CodingX 任务已开始","body":"后台任务开始执行"}', 1, 1, 0),
    (206060202, 'task-confirm-required', '任务需要确认通知', 'TASK_CONFIRM_REQUIRED', null, 'DESKTOP_NOTIFY', '{"title":"CodingX 等待确认","body":"任务执行需要你确认后继续"}', 1, 2, 0),
    (206060203, 'task-failed', '任务失败通知', 'TASK_FAILED', null, 'DESKTOP_NOTIFY', '{"title":"CodingX 任务失败","body":"后台任务执行失败，请回到会话查看原因"}', 1, 3, 0),
    (206060204, 'task-completed', '任务完成通知', 'TASK_COMPLETED', null, 'DESKTOP_NOTIFY', '{"title":"CodingX 任务完成","body":"后台任务已完成，请回到会话查看结果"}', 1, 4, 0)
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
