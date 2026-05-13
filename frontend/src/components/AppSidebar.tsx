import {Link, NavLink} from 'react-router-dom';
import {ChevronRight, Plus} from 'lucide-react';
import {shellNavigation} from '../app/navigation';
import {cn} from '../lib/utils';

/**
 * Renders the desktop sidebar with the primary CodingX workspace navigation.
 */
export function AppSidebar() {
  return (
    <aside className="hidden w-72 shrink-0 border-r border-hairline bg-canvas-card/95 md:flex md:flex-col">
      <div className="border-b border-hairline px-6 py-5">
        <div className="flex items-center justify-between">
          <div>
            <div className="text-xs font-mono uppercase tracking-[0.28em] text-body-mute">
              CodingX
            </div>
            <h1 className="mt-2 text-2xl font-semibold tracking-tight text-ink">
              Web Shell
            </h1>
          </div>
          <div className="rounded-full border border-hairline bg-canvas-soft px-3 py-1 text-xs font-mono text-body-mute">
            Vite+
          </div>
        </div>
        <Link
          to="/tasks/new"
          className="mt-5 flex w-full items-center justify-center gap-2 rounded-2xl bg-ink px-4 py-3 text-sm font-medium text-canvas transition-opacity hover:opacity-90"
        >
          <Plus className="h-4 w-4" />
          新建任务
        </Link>
      </div>

      <nav className="flex-1 px-4 py-5">
        <div className="mb-3 px-3 text-xs font-mono uppercase tracking-[0.28em] text-body-mute">
          Workspace
        </div>
        <div className="flex flex-col gap-1">
          {/* Render the canonical shell navigation from one shared source of truth. */}
          {shellNavigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({isActive}) =>
                cn(
                  'group flex items-center gap-3 rounded-2xl border px-4 py-3 text-sm transition-colors',
                  isActive
                    ? 'border-hairline-translucent bg-ink text-canvas'
                    : 'border-transparent text-body-mute hover:border-hairline hover:bg-canvas-soft hover:text-ink',
                )
              }
            >
              {({isActive}) => (
                <>
                  <item.icon className="h-4 w-4 shrink-0" />
                  <span className="flex-1">{item.label}</span>
                  <ChevronRight
                    className={cn(
                      'h-4 w-4 transition-transform',
                      isActive ? 'translate-x-0 text-canvas/80' : 'translate-x-1 text-body-mute/60',
                    )}
                  />
                </>
              )}
            </NavLink>
          ))}
        </div>
      </nav>

      <div className="border-t border-hairline px-6 py-5">
        <div className="rounded-3xl border border-hairline bg-canvas-soft p-4">
          <div className="text-xs font-mono uppercase tracking-[0.28em] text-body-mute">
            Frontend Scope
          </div>
          <p className="mt-3 text-sm leading-6 text-body">
            保留原页面主体内容，仅重构 Web 外壳、导航组件和 Vite+ 工具链。
          </p>
        </div>
      </div>
    </aside>
  );
}
