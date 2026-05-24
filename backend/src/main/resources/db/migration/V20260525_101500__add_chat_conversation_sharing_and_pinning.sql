ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS pinned SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS share_token VARCHAR(128);

COMMENT ON COLUMN chat_conversation.pinned IS '会话置顶标记，0 表示未置顶，1 表示已置顶';
COMMENT ON COLUMN chat_conversation.share_token IS '会话分享令牌，用于生成公开只读链接';

CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_pinned_updated
    ON chat_conversation (created_by, pinned DESC, updated_at DESC, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_chat_conversation_share_token
    ON chat_conversation (share_token)
    WHERE share_token IS NOT NULL;
