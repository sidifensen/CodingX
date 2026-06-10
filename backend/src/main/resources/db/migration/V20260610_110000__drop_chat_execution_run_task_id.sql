-- 删除 chat_execution_run 的遗留 task_id 列，聊天运行主键已统一使用 chat_execution_run.id。
DROP INDEX IF EXISTS idx_chat_execution_run_task;

ALTER TABLE chat_execution_run
    DROP COLUMN IF EXISTS task_id;
