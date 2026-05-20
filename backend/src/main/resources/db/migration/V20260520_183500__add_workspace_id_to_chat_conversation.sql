ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS workspace_id BIGINT;

COMMENT ON COLUMN chat_conversation.workspace_id IS '所属工作空间 ID';

CREATE INDEX IF NOT EXISTS idx_chat_conversation_workspace_user
    ON chat_conversation (created_by, workspace_id, updated_at DESC);
