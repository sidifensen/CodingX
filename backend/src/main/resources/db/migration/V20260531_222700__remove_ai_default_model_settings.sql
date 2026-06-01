-- 默认模型指针已退出路由，历史配置标记删除后由候选池 priority 决定默认顺序。
UPDATE setting
SET deleted = 1,
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key IN ('ai.chat.default_model', 'ai.chat.deep_thinking_model');
