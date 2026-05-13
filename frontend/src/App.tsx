import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom';
import {ThemeProvider} from './components/ThemeProvider';
import {AppShell} from './components/AppShell';
import {Automations} from './pages/Automations';
import {Experts} from './pages/Experts';
import {Mcp} from './pages/Mcp';
import {NewTask} from './pages/NewTask';
import {Skills} from './pages/Skills';
import {TaskDetail} from './pages/TaskDetail';
import {Tasks} from './pages/Tasks';
import {Tools} from './pages/Tools';

/**
 * Controls whether the app should create its own browser router.
 */
type AppProps = {
  withRouter?: boolean;
};

/**
 * Declares the top-level route tree used by the CodingX shell.
 */
function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<AppShell />}>
        <Route index element={<Navigate to="/tasks" replace />} />
        <Route path="tasks" element={<Tasks />} />
        <Route path="tasks/new" element={<NewTask />} />
        <Route path="task" element={<TaskDetail />} />
        <Route path="experts" element={<Experts />} />
        <Route path="skills" element={<Skills />} />
        <Route path="tools" element={<Tools />} />
        <Route path="mcp" element={<Mcp />} />
        <Route path="automations" element={<Automations />} />
      </Route>
    </Routes>
  );
}

/**
 * Bootstraps the React application and optionally wraps it with BrowserRouter.
 */
export default function App({withRouter = true}: AppProps) {
  // Reuse the same route tree in both browser mode and test mode.
  const content = <AppRoutes />;

  return (
    <ThemeProvider>
      {withRouter ? <BrowserRouter>{content}</BrowserRouter> : content}
    </ThemeProvider>
  );
}
