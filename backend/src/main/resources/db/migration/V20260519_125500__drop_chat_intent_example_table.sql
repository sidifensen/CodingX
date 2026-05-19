-- 下线历史意图示例表：先回填节点 examples，避免删表后丢失旧环境样例数据。
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

DROP INDEX IF EXISTS idx_chat_intent_example_code;
DROP TABLE IF EXISTS chat_intent_example;
