-- 将历史 KB 意图彻底下线，并替换为显式联网搜索意图，避免旧数据再次命中 KB 路由。
DELETE FROM chat_intent_node
WHERE intent_type = 'kb'
   OR intent_code IN (
       'group', 'group-hr', 'group-it', 'group-finance', 'group-finance-invoice',
       'biz', 'biz-oa', 'biz-oa-intro', 'biz-oa-security',
       'biz-ins', 'biz-ins-intro', 'biz-ins-arch', 'biz-ins-security'
   );

INSERT INTO chat_intent_node (
    id, intent_code, parent_code, name, description, intent_type, kind, kb_id, level, examples, collection_name, top_k,
    prompt_template, mcp_tool_id, param_prompt_template, prompt_snippet, enabled, sort_no, sort_order, deleted
)
VALUES
    (1998610000000000001, 'search', NULL, '联网搜索', '联网搜索意图域', 'search', 0, NULL, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 0),
    (1998610000000000002, 'search-news', 'search', '新闻资讯', '新闻热点与时效资讯检索', 'search', 0, NULL, 1, '["今天有什么科技新闻？","最近 AI 领域有什么新发布？"]', 'search_news', 5, NULL, NULL, NULL, NULL, 1, 2, 2, 0),
    (1998610000000000003, 'search-facts', 'search', '事实查询', '百科事实与参数信息检索', 'search', 0, NULL, 1, '["OpenAI 成立于哪一年？","什么是向量数据库？"]', 'search_facts', 5, NULL, NULL, NULL, NULL, 1, 3, 3, 0),
    (1998610000000000004, 'search-general', 'search', '通用检索', '开放问题的联网补充检索', 'search', 0, NULL, 1, '["帮我查一下最新 Java 版本","查询今天美元兑人民币汇率"]', 'search_general', 5, NULL, NULL, NULL, NULL, 1, 4, 4, 0)
ON CONFLICT (intent_code) DO UPDATE
SET
    parent_code = EXCLUDED.parent_code,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    intent_type = EXCLUDED.intent_type,
    kind = EXCLUDED.kind,
    kb_id = EXCLUDED.kb_id,
    level = EXCLUDED.level,
    examples = EXCLUDED.examples,
    collection_name = EXCLUDED.collection_name,
    top_k = EXCLUDED.top_k,
    prompt_template = EXCLUDED.prompt_template,
    mcp_tool_id = EXCLUDED.mcp_tool_id,
    param_prompt_template = EXCLUDED.param_prompt_template,
    prompt_snippet = EXCLUDED.prompt_snippet,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;

UPDATE chat_intent_node
SET kind = 0
WHERE intent_type = 'search'
  AND (kind IS NULL OR kind <> 0)
  AND deleted = 0;

UPDATE chat_intent_node
SET intent_type = 'search'
WHERE intent_type = 'kb'
  AND deleted = 0;
