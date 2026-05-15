import React, { useEffect, useState } from 'react';

/**
 * 描述登录弹窗组件所需输入属性。
 */
interface LoginModalProps {
  isOpen: boolean;
  isSubmitting: boolean;
  errorMessage: string;
  defaultUsername: string;
  defaultPassword: string;
  onClose: () => void;
  onSubmit: (payload: { username: string; password: string }) => Promise<void>;
}

/**
 * 渲染居中登录弹窗，并负责账号密码输入与提交流程。
 */
export default function LoginModal({
  isOpen,
  isSubmitting,
  errorMessage,
  defaultUsername,
  defaultPassword,
  onClose,
  onSubmit,
}: LoginModalProps) {
  // 步骤：维护账号输入状态。
  const [username, setUsername] = useState(defaultUsername);
  // 步骤：维护密码输入状态。
  const [password, setPassword] = useState(defaultPassword);

  useEffect(() => {
    // 步骤：弹窗打开时回填默认账号密码，便于开发环境快速登录验证。
    if (isOpen) {
      setUsername(defaultUsername);
      setPassword(defaultPassword);
    }
  }, [isOpen, defaultUsername, defaultPassword]);

  /**
   * 处理登录表单提交。
   * @param event 表单提交事件对象。
   */
  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    // 步骤：阻止表单默认提交行为，改为前端异步请求。
    event.preventDefault();
    await onSubmit({ username, password });
  };

  if (!isOpen) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/35 backdrop-blur-sm" onClick={onClose} />
      <div
        role="dialog"
        aria-modal="true"
        aria-label="登录弹窗"
        className="relative w-full max-w-md rounded-2xl border border-border bg-surface p-6 shadow-2xl"
      >
        <h2 className="text-2xl font-bold text-foreground">登录 CodingX</h2>
        <p className="mt-2 text-sm text-muted">请输入账号密码以继续使用当前工作台</p>

        <form className="mt-6 space-y-4" onSubmit={handleSubmit}>
          <div className="space-y-2">
            <label htmlFor="login-username" className="block text-sm font-medium text-foreground">
              账号
            </label>
            <input
              id="login-username"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="username"
              className="w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none transition-colors focus:border-border-active"
              placeholder="请输入账号"
            />
          </div>

          <div className="space-y-2">
            <label htmlFor="login-password" className="block text-sm font-medium text-foreground">
              密码
            </label>
            <input
              id="login-password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              className="w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none transition-colors focus:border-border-active"
              placeholder="请输入密码"
            />
          </div>

          {errorMessage ? (
            <p className="rounded-lg border border-error/30 bg-error/10 px-3 py-2 text-sm text-error">
              {errorMessage}
            </p>
          ) : null}

          <div className="flex items-center justify-end gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-border px-4 py-2 text-sm text-muted transition-colors hover:bg-surface-container hover:text-foreground"
              disabled={isSubmitting}
            >
              取消
            </button>
            <button
              type="submit"
              className="rounded-xl bg-foreground px-4 py-2 text-sm font-semibold text-background transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
              disabled={isSubmitting}
            >
              {isSubmitting ? '登录中...' : '登录'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
