-- 项目画像主表只保存每个工作空间的当前画像；旧重复扫描记录保留最新一条，其余逻辑删除。
WITH ranked_profiles AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY workspace_id
            ORDER BY scanned_at DESC NULLS LAST, updated_at DESC NULLS LAST, id DESC
        ) AS row_no
    FROM governance_project_profile
    WHERE deleted = 0
)
UPDATE governance_project_profile target
SET
    deleted = 1,
    updated_at = CURRENT_TIMESTAMP
FROM ranked_profiles ranked
WHERE target.id = ranked.id
  AND ranked.row_no > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_governance_project_profile_workspace_active
    ON governance_project_profile (workspace_id)
    WHERE deleted = 0;

COMMENT ON INDEX uk_governance_project_profile_workspace_active IS '项目画像工作空间当前记录唯一索引';
