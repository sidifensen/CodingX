import React from 'react';
import { message } from 'antd';

type AdminMessageApi = Pick<typeof message, 'error' | 'success' | 'warning'>;

const AdminMessageContext = React.createContext<AdminMessageApi | null>(null);

/**
 * 管理端全局消息浮层：用 AntD message holder 承载一次性操作反馈，避免页面内嵌 Alert 堆叠。
 */
export function AdminMessageProvider({ children }: { children: React.ReactNode }) {
  const [messageApi, contextHolder] = message.useMessage();

  return (
    <AdminMessageContext.Provider value={messageApi}>
      {contextHolder}
      {children}
    </AdminMessageContext.Provider>
  );
}

/**
 * 获取管理端消息 API；单测若未包 Provider，则回退到 AntD 静态 message 便于断言。
 */
export function useAdminMessage(): AdminMessageApi {
  return React.useContext(AdminMessageContext) ?? message;
}
