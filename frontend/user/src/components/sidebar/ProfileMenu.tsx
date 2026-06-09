import React, { useState } from 'react';
import { User, Settings, Moon, Sun, CircleHelp, LogOut } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

/**
 * 描述用户菜单组件输入属性。
 */
interface ProfileMenuProps {
  isDarkMode: boolean;
  isSubmitting: boolean;
  displayName: string;
  onToggleTheme: () => void;
  onOpenSettings: () => void;
  onLogout: () => Promise<void>;
}

/**
 * 渲染左下角个人中心入口与下拉菜单。
 */
export default function ProfileMenu({
  isDarkMode,
  isSubmitting,
  displayName,
  onToggleTheme,
  onOpenSettings,
  onLogout,
}: ProfileMenuProps) {
  // 步骤：维护下拉菜单开关状态。
  const [isProfileOpen, setIsProfileOpen] = useState(false);

  /**
   * 触发退出登录动作，并在结束后收起菜单。
   */
  const handleLogout = async () => {
    // 步骤：调用上层退出逻辑，完成后关闭当前菜单面板。
    await onLogout();
    setIsProfileOpen(false);
  };

  /**
   * 打开设置页并收起个人菜单，避免浮层遮挡设置内容。
   */
  const handleOpenSettings = () => {
    onOpenSettings();
    setIsProfileOpen(false);
  };

  // 进一步压缩左下角入口高度，同时增加底部留白，避免入口贴边。
  return (
    <div className="relative mt-auto w-full px-4 pb-4 pt-1">
      <button
        type="button"
        onClick={() => setIsProfileOpen((previousValue) => !previousValue)}
        className="flex w-full cursor-pointer items-center gap-2 rounded-lg px-2 py-1 text-left text-muted transition-all duration-200 hover:bg-surface hover:text-foreground"
      >
        <User size={16} />
        <div className="min-w-0">
          <div className="truncate text-sm leading-5 text-foreground">{displayName}</div>
          <div className="text-xs leading-3.5 text-muted">个人中心</div>
        </div>
      </button>

      <AnimatePresence>
        {isProfileOpen && (
          <motion.div
            initial={{ opacity: 0, y: 10, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 10, scale: 0.95 }}
            transition={{ duration: 0.2 }}
            className="absolute bottom-full left-4 right-4 mb-2 bg-surface text-foreground border border-border rounded-xl shadow-2xl z-50 overflow-hidden text-sm"
          >
            <div className="p-4 border-b border-border bg-background">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-surface-container border border-border overflow-hidden">
                  <div className="w-full h-full bg-surface-high flex items-center justify-center">
                    <User size={20} className="text-muted" />
                  </div>
                </div>
                <div>
                  <div className="text-foreground font-medium">{displayName}</div>
                  <div className="text-xs text-muted font-mono uppercase mt-0.5">Developer</div>
                </div>
              </div>
            </div>

            <div className="p-2 space-y-1">
              <button
                className="w-full flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-surface-container transition-colors text-muted hover:text-foreground text-left"
                onClick={handleOpenSettings}
              >
                <Settings size={18} />
                <span>设置</span>
              </button>
              <button
                onClick={onToggleTheme}
                className="w-full flex items-center justify-between px-3 py-2 rounded-lg hover:bg-surface-container transition-colors text-muted hover:text-foreground text-left"
              >
                <div className="flex items-center gap-3">
                  {isDarkMode ? <Moon size={18} /> : <Sun size={18} />}
                  <span>{isDarkMode ? '深色主题' : '浅色主题'}</span>
                </div>
                <div className="w-8 h-4 bg-foreground/20 rounded-full relative pointer-events-none">
                  <motion.div
                    layout
                    className="absolute top-0.5 w-3 h-3 bg-foreground rounded-full"
                    style={{
                      right: isDarkMode ? '2px' : 'auto',
                      left: !isDarkMode ? '2px' : 'auto',
                    }}
                  />
                </div>
              </button>
              <button className="w-full flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-surface-container transition-colors text-muted hover:text-foreground text-left">
                <CircleHelp size={18} />
                <span>帮助与反馈</span>
              </button>
              <div className="h-px bg-border my-1 mx-2" />
              <button
                className="w-full flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-error/10 text-error transition-colors text-left disabled:opacity-60 disabled:cursor-not-allowed"
                disabled={isSubmitting}
                onClick={handleLogout}
              >
                <LogOut size={18} />
                <span>{isSubmitting ? '退出中...' : '退出登录'}</span>
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
