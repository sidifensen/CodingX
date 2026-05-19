ALTER TABLE skill
    ADD COLUMN IF NOT EXISTS package_storage_format VARCHAR(32) NOT NULL DEFAULT 'zip';

COMMENT ON COLUMN skill.package_storage_format IS '技能包存储格式 directory目录 zip压缩包';
