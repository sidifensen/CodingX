-- 清理历史环境中可能残留的旧配置表，统一收敛到 setting 表。
DROP TABLE IF EXISTS chat_runtime_setting;
