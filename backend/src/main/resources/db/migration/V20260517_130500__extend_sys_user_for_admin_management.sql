ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS email VARCHAR(128),
    ADD COLUMN IF NOT EXISTS phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS last_login_ip VARCHAR(64);

COMMENT ON COLUMN sys_user.email IS '用户邮箱';
COMMENT ON COLUMN sys_user.phone IS '用户手机号';
COMMENT ON COLUMN sys_user.avatar_url IS '用户头像地址';
COMMENT ON COLUMN sys_user.last_login_at IS '最近登录时间';
COMMENT ON COLUMN sys_user.last_login_ip IS '最近登录IP';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_email
    ON sys_user (email)
    WHERE email IS NOT NULL;
