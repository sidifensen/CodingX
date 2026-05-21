-- 统一工作空间运行目标语义，仅保留 cloud 与 local，清理历史遗留类型并补齐约束。
UPDATE workspace
SET name = '历史记录',
    updated_at = NOW()
WHERE deleted = 0
  AND runtime_target = 'cloud'
  AND working_directory IS NULL;

DELETE FROM workspace
WHERE runtime_target NOT IN ('cloud', 'local');

ALTER TABLE workspace
    DROP CONSTRAINT IF EXISTS ck_workspace_runtime_target;

ALTER TABLE workspace
    ADD CONSTRAINT ck_workspace_runtime_target CHECK (runtime_target IN ('cloud', 'local'));

COMMENT ON COLUMN workspace.runtime_target IS '运行目标类型，仅支持 cloud 或 local';
