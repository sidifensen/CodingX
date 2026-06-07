import React, { useEffect, useMemo, useState } from 'react';
import { ArrowRight, KeyRound, LoaderCircle, ShieldCheck, Terminal } from 'lucide-react';
import { CliAuthApi } from '../api/cliAuthApi';
import { UserErrorMessages } from '../constants/errorMessages';
import { useAuth } from '../hooks/useAuth';
import { AuthStorage } from '../utils/authStorage';
import { AuthSession } from '../types/auth';

/**
 * CLI 登录页属性，测试可注入 navigate 避免触发真实浏览器跳转。
 */
interface CliLoginViewProps {
  navigate?: (url: string) => void;
}

/**
 * CLI 登录页面模式，loopback 用于本机回调，device 用于远程终端备用授权。
 */
type CliLoginMode = 'loopback' | 'device' | 'invalid';

/**
 * CLI 登录 URL 参数解析结果。
 */
interface CliLoginParams {
  mode: CliLoginMode;
  redirectUri: string;
  state: string;
  codeChallenge: string;
  deviceCode: string;
}

/**
 * 渲染 CLI 专用登录授权页，负责把网页登录态转换为 CLI 可保存的授权结果。
 */
export default function CliLoginView({ navigate = (url) => window.location.assign(url) }: CliLoginViewProps) {
  useEffect(() => {
    // CLI 登录页绕过主应用壳层，必须独立补齐默认暗色主题，避免直连页面时主题变量落回亮色。
    document.documentElement.classList.add('dark');
  }, []);
  // 步骤：解析当前 URL 中 CLI 传入的授权参数，避免页面和普通聊天路由混用。
  const loginParams = useMemo(() => parseCliLoginParams(window.location.search), []);
  // 步骤：复用用户端认证 hook，支持已有网页登录态恢复和账号密码登录。
  const {
    session,
    isAuthenticated,
    isSubmitting,
    errorMessage: authErrorMessage,
    login,
    clearErrorMessage,
  } = useAuth();
  // 步骤：维护本页授权请求状态，避免用户重复点击授权按钮。
  const [isAuthorizing, setIsAuthorizing] = useState(false);
  // 步骤：维护授权错误，优先展示后端返回的中文 message。
  const [authorizationError, setAuthorizationError] = useState('');
  // 步骤：维护设备码完成态；loopback 模式成功后会跳转回 CLI，不停留在此页。
  const [isDeviceAuthorized, setIsDeviceAuthorized] = useState(false);
  // 步骤：开发环境预填默认账号，保持与主应用登录弹窗一致。
  const [username, setUsername] = useState(import.meta.env.DEV ? 'admin' : '');
  const [password, setPassword] = useState(import.meta.env.DEV ? '123456' : '');
  const displayError = authorizationError || authErrorMessage;
  const busy = isSubmitting || isAuthorizing;

  /**
   * 提交账号密码并继续执行 CLI 授权。
   * @param event 表单提交事件。
   */
  const handleLoginAndAuthorize = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    clearErrorMessage();
    setAuthorizationError('');
    // 步骤 1：先完成网页登录，登录 hook 会把 token 写入 localStorage。
    await login({ username, password });
    // 步骤 2：从统一存储读取刚写入的会话，避免 useState 异步更新导致拿不到 token。
    const nextSession = AuthStorage.getSession();
    if (nextSession) {
      await authorizeCli(nextSession);
    }
  };

  /**
   * 根据当前模式执行 loopback 或设备码授权。
   * @param activeSession 当前网页登录会话。
   */
  const authorizeCli = async (activeSession: AuthSession) => {
    if (loginParams.mode === 'invalid') {
      setAuthorizationError(UserErrorMessages.CLI_AUTH_PARAMS_INVALID);
      return;
    }

    setIsAuthorizing(true);
    setAuthorizationError('');
    try {
      if (loginParams.mode === 'loopback') {
        // 步骤 1：向后端申请一次性 code，URL 回跳只带 code/state，不暴露 satoken。
        const authorization = await CliAuthApi.authorize(activeSession.token, {
          redirectUri: loginParams.redirectUri,
          state: loginParams.state,
          codeChallenge: loginParams.codeChallenge,
        });
        // 步骤 2：把 code 和 state 拼回 CLI 本机回调地址，交由 CLI 换取真正 token。
        const callbackUrl = new URL(loginParams.redirectUri);
        callbackUrl.searchParams.set('code', authorization.code);
        callbackUrl.searchParams.set('state', authorization.state);
        navigate(callbackUrl.toString());
        return;
      }

      // 步骤：设备码模式不跳转本机回调，只把 userCode 绑定到当前登录用户。
      await CliAuthApi.authorizeDevice(activeSession.token, {
        userCode: loginParams.deviceCode,
      });
      setIsDeviceAuthorized(true);
    } catch (error) {
      setAuthorizationError(error instanceof Error ? error.message : UserErrorMessages.CLI_AUTH_AUTHORIZE_FAILED);
    } finally {
      setIsAuthorizing(false);
    }
  };

  if (isDeviceAuthorized) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-background px-6 py-10 text-foreground">
        <section className="w-full max-w-xl rounded-2xl border border-border bg-surface p-8 shadow-[0_24px_80px_rgba(0,0,0,0.18)]">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-foreground text-background">
            <ShieldCheck size={24} />
          </div>
          <h1 className="mt-6 text-2xl font-semibold">授权完成，请回到终端</h1>
          <p className="mt-3 text-sm leading-6 text-muted">
            CodingX CLI 已获得当前账号授权，终端会在下一次轮询后自动继续。
          </p>
        </section>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-background text-foreground">
      <div className="mx-auto grid min-h-screen w-full max-w-6xl gap-8 px-6 py-10 lg:grid-cols-[0.9fr_1.1fr] lg:items-center">
        <section className="space-y-6">
          <div className="inline-flex h-12 w-12 items-center justify-center rounded-xl border border-border bg-surface text-foreground">
            <Terminal size={24} />
          </div>
          <div>
            <p className="text-sm font-medium uppercase tracking-[0.18em] text-muted">CodingX CLI</p>
            <h1 className="mt-3 text-4xl font-semibold leading-tight text-foreground">
              授权终端访问你的工作台
            </h1>
            <p className="mt-4 max-w-xl text-sm leading-6 text-muted">
              浏览器负责确认账号身份，终端只接收一次性授权结果。真实登录 token 不会出现在回调 URL 中。
            </p>
          </div>
          <div className="grid gap-3 text-sm text-muted">
            <FlowRow icon={<KeyRound size={16} />} text="登录后生成短期授权码" />
            <FlowRow icon={<ArrowRight size={16} />} text={loginParams.mode === 'device' ? '设备码授权后回到终端' : '授权码会跳回本机 CLI 回调地址'} />
          </div>
        </section>

        <section
          aria-label="CLI 登录授权"
          className="w-full rounded-2xl border border-border bg-surface p-6 shadow-[0_24px_80px_rgba(0,0,0,0.18)]"
        >
          <div className="flex items-start justify-between gap-4 border-b border-border pb-5">
            <div>
              <h2 className="text-xl font-semibold">
                {isAuthenticated ? '确认授权' : '登录并授权'}
              </h2>
              <p className="mt-2 text-sm text-muted">
                {loginParams.mode === 'device'
                  ? `设备验证码 ${loginParams.deviceCode || '-'}`
                  : '本机 CLI 正在等待浏览器回调'}
              </p>
            </div>
            <span className="rounded-full border border-border px-3 py-1 text-xs text-muted">
              {loginParams.mode === 'device' ? 'Device code' : 'Loopback'}
            </span>
          </div>

          {displayError ? (
            <p className="mt-5 rounded-xl border border-error/35 bg-error/10 px-4 py-3 text-sm text-error">
              {displayError}
            </p>
          ) : null}

          {isAuthenticated && session ? (
            <div className="mt-6 space-y-5">
              <div className="rounded-xl border border-border bg-background px-4 py-3">
                <p className="text-xs text-muted">当前账号</p>
                <p className="mt-1 text-base font-semibold">{session.displayName}</p>
                <p className="mt-1 text-xs text-muted">{session.username}</p>
              </div>
              <button
                type="button"
                onClick={() => authorizeCli(session)}
                disabled={busy || loginParams.mode === 'invalid'}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-foreground px-4 py-3 text-sm font-semibold text-background transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {busy ? <LoaderCircle className="animate-spin" size={16} /> : <ShieldCheck size={16} />}
                授权终端
              </button>
            </div>
          ) : (
            <form className="mt-6 space-y-4" onSubmit={handleLoginAndAuthorize}>
              <label className="block space-y-2 text-sm font-medium">
                <span>账号</span>
                <input
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  autoComplete="username"
                  className="w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none transition-colors focus:border-border-active"
                  placeholder="请输入账号"
                />
              </label>
              <label className="block space-y-2 text-sm font-medium">
                <span>密码</span>
                <input
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  type="password"
                  autoComplete="current-password"
                  className="w-full rounded-xl border border-border bg-background px-3 py-2.5 text-sm text-foreground outline-none transition-colors focus:border-border-active"
                  placeholder="请输入密码"
                />
              </label>
              <button
                type="submit"
                disabled={busy || loginParams.mode === 'invalid'}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-foreground px-4 py-3 text-sm font-semibold text-background transition-opacity hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
              >
                {busy ? <LoaderCircle className="animate-spin" size={16} /> : <ShieldCheck size={16} />}
                登录并授权
              </button>
            </form>
          )}
        </section>
      </div>
    </main>
  );
}

/**
 * 渲染一条 CLI 授权流程说明。
 * @param props 图标与文本。
 * @returns 流程行。
 */
function FlowRow({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <div className="flex items-center gap-3">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg border border-border bg-surface text-foreground">
        {icon}
      </span>
      <span>{text}</span>
    </div>
  );
}

/**
 * 从当前 URL 解析 CLI 授权参数。
 * @param search URL 查询字符串。
 * @returns 归一化后的参数。
 */
function parseCliLoginParams(search: string): CliLoginParams {
  const searchParams = new URLSearchParams(search);
  const redirectUri = searchParams.get('redirectUri') ?? '';
  const state = searchParams.get('state') ?? '';
  const codeChallenge = searchParams.get('codeChallenge') ?? '';
  const deviceCode = searchParams.get('deviceCode') ?? '';
  if (redirectUri && state && codeChallenge) {
    return {
      mode: 'loopback',
      redirectUri,
      state,
      codeChallenge,
      deviceCode: '',
    };
  }
  if (deviceCode) {
    return {
      mode: 'device',
      redirectUri: '',
      state: '',
      codeChallenge: '',
      deviceCode,
    };
  }
  return {
    mode: 'invalid',
    redirectUri: '',
    state: '',
    codeChallenge: '',
    deviceCode: '',
  };
}
