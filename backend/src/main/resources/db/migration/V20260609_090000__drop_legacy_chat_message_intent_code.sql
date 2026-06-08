-- 清理早期消息表遗留的意图字段，意图审计继续由 chat_execution_run.intent_code 承担。
ALTER TABLE chat_message
    DROP COLUMN IF EXISTS intent_code;
