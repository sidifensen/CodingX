CREATE TABLE IF NOT EXISTS chat_attachment (
    id BIGINT PRIMARY KEY,
    run_id BIGINT,
    conversation_id BIGINT,
    message_id BIGINT,
    uploaded_by BIGINT NOT NULL,
    attachment_type VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_ext VARCHAR(32),
    mime_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(1024) NOT NULL,
    preview_url VARCHAR(2048),
    content_summary TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE chat_attachment IS '聊天附件表，记录用户上传文件与消息绑定关系';
COMMENT ON COLUMN chat_attachment.id IS '附件主键 ID';
COMMENT ON COLUMN chat_attachment.run_id IS '所属执行记录 ID';
COMMENT ON COLUMN chat_attachment.conversation_id IS '所属会话 ID';
COMMENT ON COLUMN chat_attachment.message_id IS '所属消息 ID';
COMMENT ON COLUMN chat_attachment.uploaded_by IS '上传人用户 ID';
COMMENT ON COLUMN chat_attachment.attachment_type IS '附件类型 image 或 file';
COMMENT ON COLUMN chat_attachment.file_name IS '原始文件名';
COMMENT ON COLUMN chat_attachment.file_ext IS '文件扩展名';
COMMENT ON COLUMN chat_attachment.mime_type IS '文件 MIME 类型';
COMMENT ON COLUMN chat_attachment.file_size IS '文件字节大小';
COMMENT ON COLUMN chat_attachment.storage_key IS '对象存储键';
COMMENT ON COLUMN chat_attachment.preview_url IS '附件预览地址';
COMMENT ON COLUMN chat_attachment.content_summary IS '附件解析摘要';
COMMENT ON COLUMN chat_attachment.status IS '附件状态';
COMMENT ON COLUMN chat_attachment.created_at IS '创建时间';
COMMENT ON COLUMN chat_attachment.updated_at IS '更新时间';
COMMENT ON COLUMN chat_attachment.deleted IS '逻辑删除标记 0未删除 1已删除';

CREATE INDEX IF NOT EXISTS idx_chat_attachment_uploaded_by ON chat_attachment (uploaded_by, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_attachment_conversation ON chat_attachment (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_chat_attachment_message ON chat_attachment (message_id, created_at ASC);
