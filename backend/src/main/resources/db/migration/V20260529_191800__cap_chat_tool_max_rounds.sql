-- 收敛历史环境中的异常工具轮次配置，避免误配导致模型工具循环长时间占用聊天执行线程。
UPDATE setting
SET setting_value = '10',
    updated_at = CURRENT_TIMESTAMP
WHERE setting_key = 'chat.tool.max_rounds'
  AND setting_value ~ '^[0-9]+$'
  AND setting_value::INTEGER > 20;
