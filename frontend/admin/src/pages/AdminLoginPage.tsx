import { LoginOutlined, LockOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Button, Form, Input, Typography } from 'antd';
import React, { useEffect, useState } from 'react';

/**
 * 描述管理端登录页组件所需输入属性。
 */
interface AdminLoginPageProps {
  isSubmitting: boolean;
  errorMessage: string;
  defaultUsername: string;
  defaultPassword: string;
  onSubmit: (payload: { username: string; password: string }) => Promise<void>;
}

/**
 * 渲染管理端独立登录页，并负责账号密码输入与提交流程。
 */
export function AdminLoginPage({
  isSubmitting,
  errorMessage,
  defaultUsername,
  defaultPassword,
  onSubmit,
}: AdminLoginPageProps) {
  const [username, setUsername] = useState(defaultUsername);
  const [password, setPassword] = useState(defaultPassword);

  useEffect(() => {
    setUsername(defaultUsername);
    setPassword(defaultPassword);
  }, [defaultUsername, defaultPassword]);

  const handleSubmit = async () => {
    await onSubmit({ username, password });
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-background p-6 text-on-surface">
      <section className="w-full max-w-[28rem] rounded-2xl border border-border-hairline bg-surface-container-lowest p-8 shadow-[0_30px_80px_rgba(0,0,0,0.16)]">
        <div className="mb-6">
          <Typography.Title level={1} style={{ margin: 0 }}>CodingX 管理端登录</Typography.Title>
          <Typography.Text type="secondary">仅管理员账号可登录后台管理系统</Typography.Text>
        </div>

        <Form layout="vertical" onFinish={handleSubmit}>
          <Form.Item label="账号" required>
            <Input
              autoComplete="username"
              prefix={<UserOutlined />}
              value={username}
              placeholder="请输入管理员账号"
              onChange={(event) => setUsername(event.target.value)}
            />
          </Form.Item>
          <Form.Item label="密码" required>
            <Input.Password
              autoComplete="current-password"
              prefix={<LockOutlined />}
              value={password}
              placeholder="请输入密码"
              onChange={(event) => setPassword(event.target.value)}
            />
          </Form.Item>

          {errorMessage ? <Alert className="mb-md" showIcon type="error" message={errorMessage} /> : null}

          <Button block htmlType="submit" icon={<LoginOutlined />} loading={isSubmitting} type="primary">
            {isSubmitting ? '登录中...' : '登录管理端'}
          </Button>
        </Form>
      </section>
    </div>
  );
}
