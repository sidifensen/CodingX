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

  /**
   * 处理登录表单提交。
   * @param event 表单提交事件对象。
   */
  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await onSubmit({ username, password });
  };

  return (
    <div className="min-h-screen bg-background text-on-surface flex items-center justify-center p-6">
      <div className="w-full max-w-md rounded-2xl border border-border-hairline bg-surface-container-lowest p-8 shadow-[0_30px_80px_rgba(0,0,0,0.16)]">
        <div className="mb-6">
          <h1 className="font-headline-md text-headline-md font-bold text-ink">CodingX 管理端登录</h1>
          <p className="mt-2 text-body-sm text-secondary">仅管理员账号可登录后台管理系统</p>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <label htmlFor="admin-login-username" className="block text-body-sm font-medium text-ink">
              账号
            </label>
            <input
              id="admin-login-username"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              className="w-full rounded-xl border border-border-strong bg-surface px-3 py-2.5 text-body-sm text-ink outline-none transition-colors focus:border-primary"
              placeholder="请输入管理员账号"
            />
          </div>

          <div className="space-y-2">
            <label htmlFor="admin-login-password" className="block text-body-sm font-medium text-ink">
              密码
            </label>
            <input
              id="admin-login-password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              className="w-full rounded-xl border border-border-strong bg-surface px-3 py-2.5 text-body-sm text-ink outline-none transition-colors focus:border-primary"
              placeholder="请输入密码"
            />
          </div>

          {errorMessage ? (
            <p className="rounded-lg border border-error/30 bg-error-container px-3 py-2 text-body-sm text-on-error-container">
              {errorMessage}
            </p>
          ) : null}

          <button
            type="submit"
            className="w-full rounded-xl bg-primary px-4 py-2.5 text-button text-on-primary transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
            disabled={isSubmitting}
          >
            {isSubmitting ? '登录中...' : '登录管理端'}
          </button>
        </form>
      </div>
    </div>
  );
}
