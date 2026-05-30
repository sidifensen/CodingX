import React from 'react';
import { CloseOutlined } from '@ant-design/icons';
import { Alert } from 'antd';

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
      <Alert
        role="alert"
        className="pointer-events-auto w-full max-w-2xl shadow-lg"
        closeIcon={<CloseOutlined aria-label="关闭全局认证提示" />}
        closable
        message={message}
        showIcon
        type="error"
        onClose={onClose}
      />
    </div>
  );
}
