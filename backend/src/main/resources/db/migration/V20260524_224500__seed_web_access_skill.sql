-- 补充内置 web-access 技能，确保数据库技能表与类路径技能说明保持一致。
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
    package_storage_format,
    package_file_name,
    package_size,
    package_checksum,
    uploaded_by,
    uploaded_at,
    deleted
)
VALUES (
    8105,
    'web-access',
    '联网访问',
    '通过真实浏览器执行联网搜索、网页抓取与登录态页面交互',
    '网络访问',
    'built-in',
    1,
    5,
    NULL,
    'zip',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    0
)
ON CONFLICT (skill_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    storage_key = EXCLUDED.storage_key,
    package_storage_format = EXCLUDED.package_storage_format,
    package_file_name = EXCLUDED.package_file_name,
    package_size = EXCLUDED.package_size,
    package_checksum = EXCLUDED.package_checksum,
    uploaded_by = EXCLUDED.uploaded_by,
    uploaded_at = EXCLUDED.uploaded_at,
    deleted = EXCLUDED.deleted,
    updated_at = CURRENT_TIMESTAMP;
