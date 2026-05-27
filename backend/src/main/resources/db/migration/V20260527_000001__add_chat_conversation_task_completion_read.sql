ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS task_completion_read SMALLINT NOT NULL DEFAULT 1;

COMMENT ON COLUMN chat_conversation.pinned IS '会话置顶标记，0 表示未置顶，1 表示已置顶';
COMMENT ON COLUMN chat_conversation.share_token IS '会话分享令牌，用于生成公开只读链接';
COMMENT ON COLUMN chat_conversation.task_completion_read IS '任务完成提醒已读标记，0 表示未读，1 表示已读';
