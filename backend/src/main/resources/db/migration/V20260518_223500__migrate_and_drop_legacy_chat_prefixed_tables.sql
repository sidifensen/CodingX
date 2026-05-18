-- 迁移历史 chat 前缀配置表数据到新领域表，确保升级后不丢配置。
INSERT INTO skill (
    id,
    skill_code,
    display_name,
    description,
    category,
    source_type,
    enabled,
    sort_no,
    storage_key,
    package_file_name,
    package_size,
    package_checksum,
    uploaded_by,
    uploaded_at,
    created_at,
    updated_at,
    deleted
)
SELECT
    id,
    skill_code,
    display_name,
    description,
    category,
    source_type,
    enabled,
    sort_no,
    storage_key,
    package_file_name,
    package_size,
    package_checksum,
    uploaded_by,
    uploaded_at,
    created_at,
    updated_at,
    deleted
FROM chat_skill
ON CONFLICT (skill_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    storage_key = EXCLUDED.storage_key,
    package_file_name = EXCLUDED.package_file_name,
    package_size = EXCLUDED.package_size,
    package_checksum = EXCLUDED.package_checksum,
    uploaded_by = EXCLUDED.uploaded_by,
    uploaded_at = EXCLUDED.uploaded_at,
    deleted = EXCLUDED.deleted,
    updated_at = GREATEST(skill.updated_at, EXCLUDED.updated_at);

INSERT INTO mcp (
    id,
    mcp_code,
    display_name,
    description,
    category,
    source_type,
    enabled,
    sort_no,
    created_at,
    updated_at,
    deleted
)
SELECT
    id,
    mcp_code,
    display_name,
    description,
    category,
    source_type,
    enabled,
    sort_no,
    created_at,
    updated_at,
    deleted
FROM chat_mcp
ON CONFLICT (mcp_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    deleted = EXCLUDED.deleted,
    updated_at = GREATEST(mcp.updated_at, EXCLUDED.updated_at);

INSERT INTO tool (
    id,
    tool_code,
    display_name,
    description,
    category,
    source_type,
    enabled,
    sort_no,
    created_at,
    updated_at,
    deleted
)
SELECT
    id,
    tool_code,
    display_name,
    description,
    category,
    source_type,
    enabled,
    sort_no,
    created_at,
    updated_at,
    deleted
FROM chat_tool
ON CONFLICT (tool_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    deleted = EXCLUDED.deleted,
    updated_at = GREATEST(tool.updated_at, EXCLUDED.updated_at);

-- 清理历史 chat 前缀配置表，避免新旧并存导致读错表。
DROP TABLE IF EXISTS chat_tool;
DROP TABLE IF EXISTS chat_skill;
DROP TABLE IF EXISTS chat_mcp;

-- 清理历史 chat 前缀索引名，兼容可能存在的遗留对象。
DROP INDEX IF EXISTS idx_chat_tool_enabled_sort;
DROP INDEX IF EXISTS idx_chat_skill_enabled_sort;
DROP INDEX IF EXISTS idx_chat_skill_uploaded_at;
DROP INDEX IF EXISTS idx_chat_mcp_enabled_sort;
