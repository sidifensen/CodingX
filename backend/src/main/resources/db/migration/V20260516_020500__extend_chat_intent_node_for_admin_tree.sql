ALTER TABLE chat_intent_node
    ADD COLUMN IF NOT EXISTS kb_id BIGINT,
    ADD COLUMN IF NOT EXISTS level INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS examples TEXT,
    ADD COLUMN IF NOT EXISTS collection_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS top_k INTEGER,
    ADD COLUMN IF NOT EXISTS kind INTEGER,
    ADD COLUMN IF NOT EXISTS prompt_snippet TEXT,
    ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN chat_intent_node.kb_id IS '搜索域标识';
COMMENT ON COLUMN chat_intent_node.level IS '层级 0：DOMAIN 1：CATEGORY 2：TOPIC';
COMMENT ON COLUMN chat_intent_node.examples IS '示例问题JSON';
COMMENT ON COLUMN chat_intent_node.collection_name IS '搜索集合标识';
COMMENT ON COLUMN chat_intent_node.top_k IS '检索返回数量';
COMMENT ON COLUMN chat_intent_node.kind IS '类型 0：SEARCH 1：SYSTEM 2：MCP';
COMMENT ON COLUMN chat_intent_node.prompt_snippet IS '提示词摘要';
COMMENT ON COLUMN chat_intent_node.sort_order IS '管理端排序字段';

UPDATE chat_intent_node
SET kind = CASE intent_type
        WHEN 'search' THEN 0
        WHEN 'system' THEN 1
        WHEN 'mcp' THEN 2
        ELSE kind
    END
WHERE kind IS NULL
  AND deleted = 0;

UPDATE chat_intent_node
SET sort_order = COALESCE(NULLIF(sort_order, 0), sort_no, 0)
WHERE deleted = 0;

WITH aggregated_examples AS (
    SELECT
        intent_code,
        json_agg(example_text ORDER BY sort_no ASC, id ASC)::text AS examples_json
    FROM chat_intent_example
    GROUP BY intent_code
)
UPDATE chat_intent_node node
SET examples = aggregated_examples.examples_json
FROM aggregated_examples
WHERE node.intent_code = aggregated_examples.intent_code
  AND node.deleted = 0
  AND (node.examples IS NULL OR btrim(node.examples) = '');

WITH RECURSIVE intent_levels AS (
    SELECT intent_code, 0 AS node_level
    FROM chat_intent_node
    WHERE deleted = 0
      AND parent_code IS NULL
    UNION ALL
    SELECT child.intent_code, parent.node_level + 1 AS node_level
    FROM chat_intent_node child
    JOIN intent_levels parent ON child.parent_code = parent.intent_code
    WHERE child.deleted = 0
)
UPDATE chat_intent_node node
SET level = intent_levels.node_level
FROM intent_levels
WHERE node.intent_code = intent_levels.intent_code
  AND node.deleted = 0;

CREATE INDEX IF NOT EXISTS idx_chat_intent_node_kind_sort ON chat_intent_node (kind, sort_order ASC);
