import React from 'react';
import { LogIn } from 'lucide-react';

/**
 * 描述未登录入口组件输入属性。
 */
interface LoginEntryProps {
  onClick: () => void;
}

/**
 * 渲染左下角登录入口。
 */
export default function LoginEntry({ onClick }: LoginEntryProps) {
  return (
    <div className="mt-auto px-4 pb-2 pt-4 relative w-full">
      <button
        type="button"
        aria-label="侧边栏登录入口"
        onClick={onClick}
        className="flex w-full items-center gap-3 rounded-lg px-4 py-3 text-left text-muted transition-all duration-200 hover:bg-surface hover:text-foreground"
      >
        <LogIn size={20} />
        <span className="text-sm">登录</span>
      </button>
    </div>
  );
}
