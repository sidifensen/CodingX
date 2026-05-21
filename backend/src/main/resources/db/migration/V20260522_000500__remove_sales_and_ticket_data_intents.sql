-- 下线销售数据与客户工单查询意图，并级联下线其子意图，避免运行时继续命中历史链路。
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
