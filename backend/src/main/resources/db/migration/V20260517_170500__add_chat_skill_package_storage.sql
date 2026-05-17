ALTER TABLE chat_skill
    ADD COLUMN IF NOT EXISTS storage_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS package_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS package_size BIGINT,
    ADD COLUMN IF NOT EXISTS package_checksum VARCHAR(128),
    ADD COLUMN IF NOT EXISTS uploaded_by BIGINT,
    ADD COLUMN IF NOT EXISTS uploaded_at TIMESTAMP;

COMMENT ON COLUMN chat_skill.storage_key IS '技能包对象存储键';
COMMENT ON COLUMN chat_skill.package_file_name IS '技能包原始文件名';
COMMENT ON COLUMN chat_skill.package_size IS '技能包大小字节数';
COMMENT ON COLUMN chat_skill.package_checksum IS '技能包SHA256摘要';
COMMENT ON COLUMN chat_skill.uploaded_by IS '上传人用户ID';
COMMENT ON COLUMN chat_skill.uploaded_at IS '上传时间';

CREATE INDEX IF NOT EXISTS idx_chat_skill_uploaded_at ON chat_skill (uploaded_at DESC);
