INSERT INTO sys_user (id, username, display_name, password_hash, user_type, status)
VALUES
    (1001, 'admin', 'CodingX Admin', '$2b$12$6jylJFgrNWiFT.dc.qwS9.fi7vLUZXZPpgnNKR3t7.HAJ/4BF4waa', 'ADMIN', 'ACTIVE'),
    (1002, 'user', 'CodingX User', '$2b$12$rC3HC//tzz5D.YC1/fO0J.8ZIKaGzz54pHuWlrEibabJHdR0OC4uC', 'USER', 'ACTIVE')
ON CONFLICT (id) DO UPDATE
SET
    username = EXCLUDED.username,
    display_name = EXCLUDED.display_name,
    password_hash = EXCLUDED.password_hash,
    user_type = EXCLUDED.user_type,
    status = EXCLUDED.status,
    updated_at = CURRENT_TIMESTAMP,
    deleted = 0;

INSERT INTO chat_conversation (id, title, created_by, status)
VALUES (2001, 'Default Demo Conversation', 1002, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;
