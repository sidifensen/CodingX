import {Bell, Command, Moon, Search, Sun} from 'lucide-react';
import {useTheme} from './ThemeProvider';

/**
 * Renders the sticky top bar with shell shortcuts and theme controls.
 */
export function AppTopbar() {
  // Read the active theme state so the shell can expose a global toggle.
  const {theme, toggleTheme} = useTheme();

  return (
    <header className="sticky top-0 z-20 border-b border-hairline bg-canvas/85 backdrop-blur-xl">
      <div className="flex h-16 items-center gap-4 px-4 md:px-6">
        <div className="flex min-w-0 flex-1 items-center gap-4">
          <div className="md:hidden">
            <div className="rounded-full border border-hairline bg-canvas-soft px-3 py-1 text-xs font-mono uppercase tracking-[0.24em] text-body-mute">
              CodingX
            </div>
          </div>
          <div className="hidden min-w-0 md:block">
            <h2 className="truncate text-lg font-semibold tracking-tight text-ink">
              统一任务工作台
            </h2>
            <p className="text-xs font-mono uppercase tracking-[0.24em] text-body-mute">
              React + Vite+ + Tailwind CSS
            </p>
          </div>
        </div>

        <div className="hidden items-center gap-3 lg:flex">
          <div className="flex items-center gap-2 rounded-full border border-hairline bg-canvas-card px-4 py-2 text-sm text-body-mute">
            <Search className="h-4 w-4" />
            <span>搜索任务、技能、专家</span>
            <span className="rounded-full border border-hairline bg-canvas-soft px-2 py-0.5 text-[11px] font-mono">
              Ctrl K
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button className="flex h-10 w-10 items-center justify-center rounded-full border border-hairline bg-canvas-card text-body-mute transition-colors hover:text-ink">
            <Command className="h-4 w-4" />
          </button>
          <button className="flex h-10 w-10 items-center justify-center rounded-full border border-hairline bg-canvas-card text-body-mute transition-colors hover:text-ink">
            <Bell className="h-4 w-4" />
          </button>
          <button
            onClick={toggleTheme}
            className="flex h-10 w-10 items-center justify-center rounded-full border border-hairline bg-canvas-card text-body-mute transition-colors hover:text-ink"
          >
            {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </button>
        </div>
      </div>
    </header>
  );
}
