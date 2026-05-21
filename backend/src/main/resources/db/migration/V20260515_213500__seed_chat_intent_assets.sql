INSERT INTO chat_intent_node (id, intent_code, parent_code, name, description, intent_type, prompt_template, enabled, sort_no, deleted)
VALUES
    (3001, 'search', NULL, '联网搜索', '联网搜索意图域', 'search', NULL, 1, 1, 0),
    (3002, 'search-news', 'search', '新闻资讯', '新闻热点与时效资讯检索', 'search', NULL, 1, 2, 0),
    (3003, 'search-facts', 'search', '事实查询', '百科事实与参数信息检索', 'search', NULL, 1, 3, 0),
    (3004, 'search-general', 'search', '通用检索', '开放问题的联网补充检索', 'search', NULL, 1, 4, 0),
    (3201, 'sys', NULL, '系统交互', '系统交互相关意图域', 'system', NULL, 1, 20, 0),
    (3202, 'sys-welcome', 'sys', '欢迎与问候', '用户与助手打招呼，如你好、hello、在吗等', 'system', NULL, 1, 21, 0),
    (3203, 'sys-about-bot', 'sys', '关于助手', '询问助手是做什么的、是谁、能做什么等', 'system', NULL, 1, 22, 0),
    (3301, 'sales', NULL, '销售汇总数据统计', 'MCP 实时销售数据查询域，当前预留未启用', 'mcp', NULL, 0, 30, 0),
    (3302, 'sales-data', 'sales', '销售数据统计', '销售总额、销售量、销售占比、趋势等实时统计，当前预留未启用', 'mcp', NULL, 0, 31, 0)
ON CONFLICT (id) DO UPDATE
SET
    intent_code = EXCLUDED.intent_code,
    parent_code = EXCLUDED.parent_code,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    intent_type = EXCLUDED.intent_type,
    prompt_template = EXCLUDED.prompt_template,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;

INSERT INTO chat_intent_example (id, intent_code, example_text, sort_no)
VALUES
    (4001, 'search-news', '今天有什么科技新闻？', 1),
    (4002, 'search-news', '最近 AI 领域有什么新发布？', 2),
    (4003, 'search-facts', 'OpenAI 成立于哪一年？', 1),
    (4004, 'search-facts', '什么是向量数据库？', 2),
    (4005, 'search-general', '帮我查一下最新 Java 版本', 1),
    (4006, 'search-general', '查询今天美元兑人民币汇率', 2),
    (4017, 'sys-welcome', '你好', 1),
    (4018, 'sys-welcome', 'hello', 2),
    (4019, 'sys-welcome', '在吗', 3),
    (4020, 'sys-about-bot', '你是谁', 1),
    (4021, 'sys-about-bot', '你能帮我做什么', 2),
    (4022, 'sys-about-bot', '你是什么AI', 3),
    (4023, 'sales-data', '销售总额是多少？', 1),
    (4024, 'sales-data', '销售量是多少？', 2)
ON CONFLICT (id) DO UPDATE
SET
    intent_code = EXCLUDED.intent_code,
    example_text = EXCLUDED.example_text,
    sort_no = EXCLUDED.sort_no;

INSERT INTO chat_query_term_mapping (id, source_term, target_term, match_type, priority, enabled, remark, deleted)
VALUES
    (5001, 'oa', 'OA系统', 1, 1, 1, '系统简称归一化', 0),
    (5002, 'vpn', 'VPN', 1, 2, 1, '网络术语归一化', 0),
    (5003, 'rag', '检索增强生成', 1, 3, 1, 'AI术语归一化', 0),
    (5004, 'llm', '大语言模型', 1, 4, 1, 'AI术语归一化', 0)
ON CONFLICT (id) DO UPDATE
SET
    source_term = EXCLUDED.source_term,
    target_term = EXCLUDED.target_term,
    match_type = EXCLUDED.match_type,
    priority = EXCLUDED.priority,
    enabled = EXCLUDED.enabled,
    remark = EXCLUDED.remark,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;

INSERT INTO chat_sample_question (id, question_text, category, enabled, sort_no, deleted)
VALUES
    (6001, '请介绍一下 OA 系统的主要功能', '业务系统', 1, 1, 0),
    (6002, '公司 VPN 连不上怎么办？', 'IT支持', 1, 2, 0),
    (6003, '你是谁，你能帮我做什么？', '系统交互', 1, 3, 0),
    (6004, '差旅报销需要准备哪些材料？', '财务', 1, 4, 0)
ON CONFLICT (id) DO UPDATE
SET
    question_text = EXCLUDED.question_text,
    category = EXCLUDED.category,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
