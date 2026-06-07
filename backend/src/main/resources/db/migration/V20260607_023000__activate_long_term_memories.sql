-- 长期记忆取消人工确认门槛后，历史待确认记录应立即参与后续上下文回注。
UPDATE governance_long_term_memory
SET status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'PENDING'
  AND deleted = 0;
