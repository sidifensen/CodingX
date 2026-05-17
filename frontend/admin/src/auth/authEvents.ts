const ADMIN_AUTH_EXPIRED_EVENT = 'codingx:admin-auth-expired';

interface AdminAuthExpiredEventDetail {
  message?: string;
}

/**
 * 发布管理端会话失效事件，供认证状态层统一回收登录态与跳转逻辑。
 * @param message 可选提示文案，优先透传后端返回的中文错误信息。
 */
export function publishAdminAuthExpired(message?: string): void {
  window.dispatchEvent(
    new CustomEvent<AdminAuthExpiredEventDetail>(ADMIN_AUTH_EXPIRED_EVENT, {
      detail: { message },
    }),
  );
}

/**
 * 订阅管理端会话失效事件。
 * @param listener 事件回调，接收可选中文提示文案。
 * @returns 取消订阅函数。
 */
export function subscribeAdminAuthExpired(listener: (message?: string) => void): () => void {
  const handler = (event: Event) => {
    const customEvent = event as CustomEvent<AdminAuthExpiredEventDetail>;
    listener(customEvent.detail?.message);
  };

  window.addEventListener(ADMIN_AUTH_EXPIRED_EVENT, handler as EventListener);
  return () => {
    window.removeEventListener(ADMIN_AUTH_EXPIRED_EVENT, handler as EventListener);
  };
}
