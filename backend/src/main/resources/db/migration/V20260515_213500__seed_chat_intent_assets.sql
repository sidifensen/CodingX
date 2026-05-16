INSERT INTO chat_intent_node (id, intent_code, parent_code, name, description, intent_type, prompt_template, enabled, sort_no, deleted)
VALUES
    (3001, 'group', NULL, '集团信息化', '集团信息化相关知识域', 'kb', NULL, 1, 1, 0),
    (3002, 'group-hr', 'group', '人事', '招聘、入职、转正、离职、绩效、薪资、考勤、请假等人力资源相关问题', 'kb', NULL, 1, 2, 0),
    (3003, 'group-it', 'group', 'IT支持', 'VPN、邮箱、打印机、网络、电脑账号密码、办公软件等 IT 支持相关问题', 'kb', NULL, 1, 3, 0),
    (3004, 'group-finance', 'group', '财务', '报销、付款、成本中心、预算等财务相关问题', 'kb', NULL, 1, 4, 0),
    (3005, 'group-finance-invoice', 'group-finance', '发票相关', '获取公司发票抬头相关信息', 'kb', NULL, 1, 5, 0),
    (3101, 'biz', NULL, '业务系统', '业务系统相关知识域', 'kb', NULL, 1, 10, 0),
    (3102, 'biz-oa', 'biz', 'OA系统', 'OA 系统相关，例如流程审批、待办、公告、文档中心等', 'kb', NULL, 1, 11, 0),
    (3103, 'biz-oa-intro', 'biz-oa', '系统介绍', 'OA 系统整体功能说明、主要模块、典型使用场景', 'kb', NULL, 1, 12, 0),
    (3104, 'biz-oa-security', 'biz-oa', '数据安全', 'OA系统的数据权限、访问控制、安全审计等相关说明', 'kb', NULL, 1, 13, 0),
    (3105, 'biz-ins', 'biz', '保险系统', '保险相关业务系统，如投保、核保、理赔等的功能与架构说明', 'kb', NULL, 1, 14, 0),
    (3106, 'biz-ins-intro', 'biz-ins', '系统介绍', '保险系统业务模块说明与主要流程介绍', 'kb', NULL, 1, 15, 0),
    (3107, 'biz-ins-arch', 'biz-ins', '架构设计', '保险系统的技术架构、服务拆分、数据库设计等', 'kb', NULL, 1, 16, 0),
    (3108, 'biz-ins-security', 'biz-ins', '数据安全', '保险系统的数据脱敏、权限控制、审计与合规等', 'kb', NULL, 1, 17, 0),
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
    (4001, 'group-hr', '请假流程是怎样的？', 1),
    (4002, 'group-hr', '试用期多久转正？', 2),
    (4003, 'group-hr', '迟到会有什么处罚？', 3),
    (4004, 'group-it', '公司 VPN 连不上怎么办？', 1),
    (4005, 'group-it', '电脑打印机怎么连？', 2),
    (4006, 'group-it', '邮箱密码忘了怎么重置？', 3),
    (4007, 'group-finance', '差旅报销需要哪些资料？', 1),
    (4008, 'group-finance-invoice', '发票抬头有哪些？', 1),
    (4009, 'biz-oa', 'OA系统主要提供哪些功能？', 1),
    (4010, 'biz-oa', '请假审批在哪个菜单？', 2),
    (4011, 'biz-oa-intro', 'OA系统是做什么的？', 1),
    (4012, 'biz-oa-security', 'OA系统如何控制不同角色的权限？', 1),
    (4013, 'biz-ins', '保险系统整体架构是怎样的？', 1),
    (4014, 'biz-ins-intro', '保险系统都包括哪些子系统？', 1),
    (4015, 'biz-ins-arch', '保险系统是如何做服务拆分的？', 1),
    (4016, 'biz-ins-security', '保险系统的敏感信息如何保护？', 1),
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
