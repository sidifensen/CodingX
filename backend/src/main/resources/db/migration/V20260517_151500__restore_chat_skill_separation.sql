CREATE TABLE IF NOT EXISTS chat_skill (
    id BIGINT PRIMARY KEY,
    skill_code VARCHAR(128) NOT NULL UNIQUE,
    display_name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(128),
    source_type VARCHAR(64) NOT NULL DEFAULT 'built-in',
    enabled SMALLINT NOT NULL DEFAULT 1,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE chat_skill IS '聊天技能配置表';
COMMENT ON COLUMN chat_skill.id IS '技能主键ID';
COMMENT ON COLUMN chat_skill.skill_code IS '技能编码';
COMMENT ON COLUMN chat_skill.display_name IS '技能名称';
COMMENT ON COLUMN chat_skill.description IS '技能描述';
COMMENT ON COLUMN chat_skill.category IS '技能分类';
COMMENT ON COLUMN chat_skill.source_type IS '技能来源';
COMMENT ON COLUMN chat_skill.enabled IS '是否启用 1启用 0禁用';
COMMENT ON COLUMN chat_skill.sort_no IS '排序字段';
COMMENT ON COLUMN chat_skill.created_at IS '创建时间';
COMMENT ON COLUMN chat_skill.updated_at IS '更新时间';
COMMENT ON COLUMN chat_skill.deleted IS '是否删除 0正常 1删除';

CREATE TABLE IF NOT EXISTS task_skill (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    skill_code VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE task_skill IS '任务技能绑定表';
COMMENT ON COLUMN task_skill.id IS '主键ID';
COMMENT ON COLUMN task_skill.task_id IS '任务ID';
COMMENT ON COLUMN task_skill.skill_code IS '技能编码';
COMMENT ON COLUMN task_skill.created_at IS '创建时间';

CREATE INDEX IF NOT EXISTS idx_chat_skill_enabled_sort ON chat_skill (enabled, sort_no ASC);
CREATE INDEX IF NOT EXISTS idx_task_skill_task ON task_skill (task_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_task_skill_skill_code ON task_skill (skill_code);

-- 兼容历史版本：若 chat_mcp 中仍留有旧技能数据（source_type=skill），迁移到 chat_skill。
INSERT INTO chat_skill (id, skill_code, display_name, description, category, source_type, enabled, sort_no, created_at, updated_at, deleted)
SELECT
    id,
    mcp_code AS skill_code,
    display_name,
    description,
    category,
    source_type,
    enabled,
    sort_no,
    created_at,
    updated_at,
    deleted
FROM chat_mcp
WHERE source_type = 'skill'
ON CONFLICT (skill_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;

-- 迁移保底初始化：确保技能列表不为空。
INSERT INTO chat_skill (id, skill_code, display_name, description, category, source_type, enabled, sort_no, deleted)
VALUES
    (8101, 'web-read', '网页读取', '解析并总结外部网页内容', '信息处理', 'built-in', 1, 1, 0),
    (8102, 'deep-research', '调研分析', '深度搜索并生成研究报告', '研究分析', 'built-in', 1, 2, 0),
    (8103, 'data-mining', '数据挖掘', '结构化数据提取与清洗', '数据处理', 'built-in', 1, 3, 0),
    (8104, 'file-manage', '文件管理', '上传并与您的文档进行对话', '文档处理', 'built-in', 1, 4, 0)
ON CONFLICT (skill_code) DO UPDATE
SET
    display_name = EXCLUDED.display_name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    source_type = EXCLUDED.source_type,
    enabled = EXCLUDED.enabled,
    sort_no = EXCLUDED.sort_no,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
