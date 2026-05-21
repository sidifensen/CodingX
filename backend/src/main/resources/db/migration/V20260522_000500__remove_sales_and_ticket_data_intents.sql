-- 下线销售与工单查询意图，并级联下线其子意图；后续由物理清理迁移彻底移除历史数据。
WITH RECURSIVE target AS (
    SELECT intent_code
    FROM chat_intent_node
    WHERE deleted = 0
      AND intent_code IN ('sales-data', 'ticket-data')
    UNION ALL
    SELECT child.intent_code
    FROM chat_intent_node child
    JOIN target parent ON child.parent_code = parent.intent_code
    WHERE child.deleted = 0
)
UPDATE chat_intent_node
SET deleted = 1,
    enabled = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE intent_code IN (SELECT intent_code FROM target);
