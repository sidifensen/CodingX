import {Outlet, useLocation} from 'react-router-dom';
import {AnimatePresence, motion} from 'motion/react';
import {AppSidebar} from './AppSidebar';
import {AppTopbar} from './AppTopbar';

/**
 * Renders the persistent shell chrome and animates route content changes.
 */
export function AppShell() {
  // Track the pathname so page transitions can animate per route.
  const location = useLocation();

  return (
    <div className="min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(255,122,23,0.12),_transparent_28%),linear-gradient(180deg,_rgba(255,255,255,0.98),_rgba(245,245,245,0.94))] text-body dark:bg-[radial-gradient(circle_at_top_left,_rgba(255,122,23,0.14),_transparent_24%),linear-gradient(180deg,_rgba(10,10,10,0.98),_rgba(18,18,18,0.98))]">
      <div className="flex min-h-screen">
        <AppSidebar />
        <div className="flex min-w-0 flex-1 flex-col">
          <AppTopbar />
          <main className="flex-1 min-w-0">
            <AnimatePresence mode="wait">
              <motion.div
                key={location.pathname}
                initial={{opacity: 0, y: 10}}
                animate={{opacity: 1, y: 0}}
                exit={{opacity: 0, y: -8}}
                transition={{duration: 0.18, ease: 'easeOut'}}
                className="min-h-[calc(100vh-4rem)]"
              >
                <Outlet />
              </motion.div>
            </AnimatePresence>
          </main>
        </div>
      </div>
    </div>
  );
}
