-- 物理删除代码检索 MCP 配置、运行时设置与历史意图节点，防止已下线能力继续出现在管理端和运行时。
WITH RECURSIVE target AS (
    SELECT intent_code
    FROM chat_intent_node
    WHERE intent_code IN ('code', 'code-search')
    UNION ALL
    SELECT child.intent_code
    FROM chat_intent_node child
    JOIN target parent ON child.parent_code = parent.intent_code
)
DELETE FROM chat_intent_node
WHERE intent_code IN (SELECT intent_code FROM target);

DELETE FROM setting
WHERE setting_key LIKE 'code_search.%'
   OR category_code = 'code_search';

DELETE FROM mcp
WHERE mcp_code = 'code_search';
