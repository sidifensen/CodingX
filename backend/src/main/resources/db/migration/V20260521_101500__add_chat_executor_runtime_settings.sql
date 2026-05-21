INSERT INTO setting (id, setting_key, setting_value, value_type, category_code, description, sort_no, restart_required, deleted)
VALUES
    (7060, 'chat.executor.stream_core_pool_size', '2', 'INTEGER', 'chat.executor', '聊天入口线程池核心线程数', 10, TRUE, 0),
    (7061, 'chat.executor.stream_max_pool_size', '8', 'INTEGER', 'chat.executor', '聊天入口线程池最大线程数', 20, TRUE, 0),
    (7062, 'chat.executor.stream_queue_capacity', '256', 'INTEGER', 'chat.executor', '聊天入口线程池队列容量', 30, TRUE, 0),
    (7063, 'chat.executor.search_core_pool_size', '4', 'INTEGER', 'chat.executor', '搜索线程池核心线程数', 40, TRUE, 0),
    (7064, 'chat.executor.search_max_pool_size', '8', 'INTEGER', 'chat.executor', '搜索线程池最大线程数', 50, TRUE, 0),
    (7065, 'chat.executor.search_queue_capacity', '256', 'INTEGER', 'chat.executor', '搜索线程池队列容量', 60, TRUE, 0),
    (7066, 'chat.executor.keep_alive_seconds', '60', 'LONG', 'chat.executor', '聊天线程池保活秒数', 70, TRUE, 0)
ON CONFLICT (setting_key) DO UPDATE
SET
    setting_value = EXCLUDED.setting_value,
    value_type = EXCLUDED.value_type,
    category_code = EXCLUDED.category_code,
    description = EXCLUDED.description,
    sort_no = EXCLUDED.sort_no,
    restart_required = EXCLUDED.restart_required,
    updated_at = CURRENT_TIMESTAMP,
    deleted = EXCLUDED.deleted;
