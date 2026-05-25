import React from 'react';
import { BrowserRouter as Router, Navigate, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Dashboard } from './pages/Dashboard';
import { Users } from './pages/Users';
import { UserDetail } from './pages/UserDetail';
import { Tasks } from './pages/Tasks';
import { TaskDetail } from './pages/TaskDetail';
import { Skills } from './pages/Skills';
import { Experts } from './pages/Experts';
import { ToolsPage } from './pages/ToolsPage';
import { WorkspacePage } from './pages/WorkspacePage';
import { WorkspaceDetailPage } from './pages/WorkspaceDetailPage';
import { MCP } from './pages/MCP';
import { Settings } from './pages/Settings';
import { Notifications } from './pages/Notifications';
import { AdminLoginPage } from './pages/AdminLoginPage';
import { useAdminAuth } from './hooks/useAdminAuth';
import { TracePage } from './pages/TracePage';
import { TraceDetailPage } from './pages/traces/TraceDetailPage';
import { IntentTreePage } from './pages/IntentTreePage';
import { QueryTermMappingPage } from './pages/QueryTermMappingPage';
import { FeedbackPage } from './pages/FeedbackPage';
import { FeedbackDetailPage } from './pages/FeedbackDetailPage';
import { FeedbackReferencePage } from './pages/FeedbackReferencePage';
import { GlobalAuthNotice } from './components/GlobalAuthNotice';

/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export default function App() {
  // 步骤：统一托管管理端认证状态，确保登录页与后台页面共用同一会话上下文。
  const { isAuthenticated, isSubmitting, errorMessage, login, logout, clearErrorMessage, isBootstrapping } = useAdminAuth();
  // 步骤：在开发环境预填管理员账号密码，降低本地联调成本。
  const isDevelopmentMode = import.meta.env.DEV;
  const loginDefaultUsername = isDevelopmentMode ? 'admin' : '';
  const loginDefaultPassword = isDevelopmentMode ? '123456' : '';

  /**
   * 提交管理端登录表单。
   * @param payload 登录参数。
   */
  const handleLoginSubmit = async (payload: { username: string; password: string }) => {
    try {
      await login(payload);
    } catch {
      // 步骤：登录失败时错误文案已由 useAdminAuth 维护，这里无需额外处理。
    }
  };

  return (
    <Router>
      <GlobalAuthNotice message={errorMessage} onClose={clearErrorMessage} />
      <Routes>
        <Route
          path="/login"
          element={
            isAuthenticated ? (
              <Navigate to="/" replace />
            ) : (
              <AdminLoginPage
                isSubmitting={isSubmitting}
                errorMessage={errorMessage}
                defaultUsername={loginDefaultUsername}
                defaultPassword={loginDefaultPassword}
                onSubmit={handleLoginSubmit}
              />
            )
          }
        />
        <Route
          path="/"
          element={
            isBootstrapping ? (
              <AdminGuardLoading />
            ) : isAuthenticated ? (
              <Layout onLogout={logout} isAuthSubmitting={isSubmitting} />
            ) : (
              <Navigate to="/login" replace />
            )
          }
        >
          <Route index element={<Dashboard />} />
          <Route path="users" element={<Users />} />
          <Route path="users/:id" element={<UserDetail />} />
          <Route path="tasks" element={<Tasks />} />
          <Route path="tasks/:id" element={<TaskDetail />} />
          <Route path="workspaces" element={<WorkspacePage />} />
          <Route path="workspaces/:workspaceId" element={<WorkspaceDetailPage />} />
          <Route path="skills" element={<Skills />} />
          <Route path="experts" element={<Experts />} />
          <Route path="tools" element={<ToolsPage />} />
          <Route path="mcp" element={<MCP />} />
          <Route path="traces" element={<TracePage />} />
          <Route path="traces/:traceId" element={<TraceDetailPage />} />
          <Route path="intent-tree" element={<IntentTreePage />} />
          <Route path="query-term-mappings" element={<QueryTermMappingPage />} />
          <Route path="feedbacks" element={<FeedbackPage />} />
          <Route path="feedbacks/:feedbackId" element={<FeedbackDetailPage />} />
          <Route path="feedbacks/:feedbackId/references" element={<FeedbackReferencePage />} />
          <Route path="settings" element={<Settings />} />
          <Route path="notifications" element={<Notifications />} />
        </Route>
        <Route path="*" element={<Navigate to={isAuthenticated ? '/' : '/login'} replace />} />
      </Routes>
    </Router>
  );
}

/**
 * 管理端认证校验中的加载占位，避免会话校验前闪现后台页面内容。
 */
function AdminGuardLoading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-background px-6 text-secondary">
      正在校验登录状态...
    </div>
  );
}
