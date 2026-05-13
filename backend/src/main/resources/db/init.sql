INSERT INTO sys_user (id, username, display_name, password_hash, user_type, status)
VALUES
    (1001, 'admin', 'CodingX Admin', '$2a$10$hiM8SA2fF5Jh2aGZpzfdWuBrvcOngAoLwAg0FuBCligX1ZsX3vi62', 'ADMIN', 'ACTIVE'),
    (1002, 'demo', 'CodingX Demo', '$2a$10$8uPQ3988ol5cHfP5uwrFieCDSReMk.ft0qgvP92MTvnbnamyD0Opq', 'USER', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO cx_chat_conversation (id, title, created_by, status)
VALUES (2001, 'Default Demo Conversation', 1002, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;