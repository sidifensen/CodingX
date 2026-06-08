import React from 'react';

interface ChatMessageListProps {
  hasMoreBefore: boolean;
  isLoadingOlder: boolean;
  onLoadOlderMessages: () => Promise<void>;
  children: React.ReactNode;
}

/**
 * 承载聊天消息列表的滚动内容外壳，并在顶部提供旧消息分页入口。
 */
export default function ChatMessageList({
  hasMoreBefore,
  isLoadingOlder,
  onLoadOlderMessages,
  children,
}: ChatMessageListProps) {
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      {hasMoreBefore ? (
        <div className="flex justify-center">
          <button
            type="button"
            aria-label="加载更早消息"
            disabled={isLoadingOlder}
            onClick={() => void onLoadOlderMessages()}
            className="inline-flex h-8 items-center rounded-lg bg-surface-container px-3 text-xs font-medium text-muted transition-colors hover:bg-surface-container-high hover:text-foreground disabled:cursor-not-allowed disabled:opacity-60"
          >
            {isLoadingOlder ? '加载中...' : '加载更早消息'}
          </button>
        </div>
      ) : null}
      {children}
    </div>
  );
}
