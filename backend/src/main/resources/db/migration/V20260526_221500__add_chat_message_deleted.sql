ALTER TABLE chat_message
    ADD COLUMN IF NOT EXISTS deleted SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN chat_message.deleted IS '逻辑删除标记，0 表示未删除，1 表示已删除';

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_deleted
    ON chat_message (conversation_id, deleted, created_at ASC);
