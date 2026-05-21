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
  // 进一步压缩登录入口高度，同时增加底部留白，避免左下角入口贴边。
  return (
    <div className="relative mt-auto w-full px-4 pb-4 pt-1">
      <button
        type="button"
        aria-label="侧边栏登录入口"
        onClick={onClick}
        className="flex w-full items-center gap-2 rounded-lg text-left text-muted transition-all duration-200 hover:bg-surface hover:text-foreground"
      >
        <LogIn size={16} />
        <span className="text-sm leading-5">登录</span>
      </button>
    </div>
  );
}
