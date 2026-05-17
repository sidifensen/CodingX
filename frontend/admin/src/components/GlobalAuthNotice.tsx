import React from 'react';

interface GlobalAuthNoticeProps {
  message: string;
  onClose: () => void;
}

/**
 * 展示管理端全局认证提示，统一承载登录失效等需要跨页面感知的错误信息。
 */
export function GlobalAuthNotice({ message, onClose }: GlobalAuthNoticeProps) {
  if (!message) {
    return null;
  }

  return (
    <div className="pointer-events-none fixed top-4 left-0 z-[90] flex w-full justify-center px-4">
      <div
        role="alert"
        className="pointer-events-auto flex w-full max-w-2xl items-start gap-3 rounded-xl border border-error bg-error-container px-4 py-3 text-on-error-container shadow-lg"
      >
        <span className="material-symbols-outlined mt-0.5 text-[18px]">error</span>
        <p className="flex-1 text-body-sm">{message}</p>
        <button
          type="button"
          aria-label="关闭全局认证提示"
          className="rounded-md p-1 text-on-error-container transition-colors hover:bg-on-error-container/10"
          onClick={onClose}
        >
          <span className="material-symbols-outlined text-[18px]">close</span>
        </button>
      </div>
    </div>
  );
}
