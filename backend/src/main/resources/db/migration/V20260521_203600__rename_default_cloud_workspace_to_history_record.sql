-- 将默认云端空间名称统一为“历史记录”，避免云端/本地文案不一致。
UPDATE workspace
SET name = '历史记录',
    updated_at = NOW()
WHERE deleted = 0
  AND runtime_target = 'cloud'
  AND working_directory IS NULL
  AND name IN ('默认云端空间', '云端工作空间', '本地工作空间', '历史会话');
