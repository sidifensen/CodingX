-- 同步来自 GitHub 的通用热门技能，并绑定对应 RustFS 目录化包。
-- storage_key 必须与对象存储目录前缀一致，运行时会读取 <storage_key>/SKILL.md。
INSERT INTO skill (
    id,
    skill_code,
    display_name,
    description,
    category,
    source_type,
    enabled,
    sort_no,
    storage_key,
    package_storage_format,
    package_file_name,
    package_size,
    package_checksum,
    uploaded_by,
    uploaded_at,
    deleted
)
VALUES
    (9001, 'multi-search', 'multi-search', '聚合多搜索源执行联网检索、缓存与结果整理，适合需要横向搜索多个来源并汇总网页证据的任务', '网络搜索', 'https://github.com/Nex-ZMH/Agent-websearch-skill', 1, 1, 'chat-skills/packages/agent-websearch-skill', 'directory', 'agent-websearch-skill', 154024, 'c3f33e7847cfef7c269855ec9f7ec2eb5b25fc5c80a91a08bf16007e65b41d59', NULL, CURRENT_TIMESTAMP, 0),
    (9002, 'web-access', 'web-access', '通过真实浏览器执行联网搜索、网页抓取与登录态页面交互', '网络工具', 'https://github.com/eze-is/web-access', 1, 2, 'chat-skills/packages/web-access', 'directory', 'web-access', 93668, '01b93c4ba2ef3565e587de1811f4d0dcb90f05908a32dbbf157b551988690145', NULL, CURRENT_TIMESTAMP, 0),
    (9003, 'agent-skill-creator', 'agent-skill-creator', '将工作流、资料或脚本转化为跨平台 agent skill，适用于创建、校验、迁移和导出可复用技能', '技能开发', 'https://github.com/FrancyJGLisboa/agent-skill-creator', 1, 3, 'chat-skills/packages/agent-skill-creator', 'directory', 'agent-skill-creator', 1000552, '316df02f0d55f3f5f1385b3ed610919873fe9bac77077ccfb6cfae674c9cd3cb', NULL, CURRENT_TIMESTAMP, 0),
    (9004, 'humanizer', 'humanizer', '识别并改写 AI 生成文本痕迹，让文案、说明和长文更自然、更贴近真实作者语气', '写作润色', 'https://github.com/blader/humanizer', 1, 4, 'chat-skills/packages/humanizer', 'directory', 'humanizer', 45208, '29761455b17e48cd450a78785d440b389c521d21ec2caccd664b0cd7e933aa24', NULL, CURRENT_TIMESTAMP, 0),
    (9005, 'design-professional', 'professional design', '提供专业、可信、业务就绪的界面设计系统指导，适用于企业级页面和结构化产品界面设计', '设计系统', 'https://github.com/bergside/awesome-design-skills', 1, 5, 'chat-skills/packages/design-professional', 'directory', 'professional-design', 4947, '8677adf807251683901cc1106f6806cda4be8c8e2e5e39fc016ba0896eaf9caf', NULL, CURRENT_TIMESTAMP, 0),
    (9006, 'user-research', 'user-research', '指导用户访谈、画像、问卷、可用性测试和研究结论沉淀，帮助产品团队用证据理解用户需求', '产品研究', 'https://github.com/TerminalSkills/skills', 1, 6, 'chat-skills/packages/user-research', 'directory', 'user-research', 11191, 'bac8e8aa82f2dd61ee910de87cb905cc26551bc5649ba144ed61f4acfdb56b37', NULL, CURRENT_TIMESTAMP, 0),
    (9007, 'web-research', 'web-research', '围绕主题制定研究计划、检索网页资料、提取来源并汇总成带引用的结构化研究报告', '研究分析', 'https://github.com/TerminalSkills/skills', 1, 7, 'chat-skills/packages/web-research', 'directory', 'web-research', 10392, '5afc23dba16ed02a404b2d29bea94d89d17a85ce0d4d2c789a8c991b9e4ed7da', NULL, CURRENT_TIMESTAMP, 0)
ON CONFLICT (skill_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    storage_key = EXCLUDED.storage_key,
    package_storage_format = EXCLUDED.package_storage_format,
    package_file_name = EXCLUDED.package_file_name,
    package_size = EXCLUDED.package_size,
    package_checksum = EXCLUDED.package_checksum,
    uploaded_by = EXCLUDED.uploaded_by,
    uploaded_at = COALESCE(skill.uploaded_at, EXCLUDED.uploaded_at),
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
