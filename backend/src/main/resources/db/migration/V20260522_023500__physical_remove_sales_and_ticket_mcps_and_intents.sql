-- 物理删除销售与工单 MCP 配置、关联绑定与历史意图节点，防止运行时继续暴露已下线能力。
WITH RECURSIVE target AS (
    SELECT intent_code
    FROM chat_intent_node
    WHERE intent_code IN ('sales', 'sales-data', 'ticket', 'ticket-data')
    UNION ALL
    SELECT child.intent_code
    FROM chat_intent_node child
    JOIN target parent ON child.parent_code = parent.intent_code
)
DELETE FROM chat_intent_node
WHERE intent_code IN (SELECT intent_code FROM target);

DELETE FROM task_mcp
WHERE mcp_code IN ('sales_query', 'ticket_query');

DELETE FROM mcp
WHERE mcp_code IN ('sales_query', 'ticket_query');
