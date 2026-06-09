import { useCallback, useEffect, useMemo, useState } from 'react';

import { AutomationApi } from '../../api/automationApi';
import { AuthStorage } from '../../utils/authStorage';
import { AutomationTask, AutomationTaskCreatePayload, AutomationTaskUpdatePayload } from './types';

/**
 * 聚合自动化任务列表和创建流程，隔离页面组件中的请求副作用。
 */
export function useAutomationTasks() {
  const [tasks, setTasks] = useState<AutomationTask[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  /**
   * 读取当前 token；自动化页不是登录页，缺失 token 时只展示中文错误，不主动弹原生提示。
   */
  const currentToken = useCallback(() => AuthStorage.getSession()?.token ?? null, []);

  /**
   * 从后端刷新任务列表；后端错误 message 会经 API 层直接抛出并展示。
   */
  const reloadTasks = useCallback(async () => {
    const token = currentToken();
    if (!token) {
      setTasks([]);
      setErrorMessage('请先登录后再管理自动化任务');
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    try {
      const nextTasks = await AutomationApi.listTasks(token);
      setTasks(nextTasks);
      setErrorMessage('');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '自动化任务请求失败');
    } finally {
      setIsLoading(false);
    }
  }, [currentToken]);

  useEffect(() => {
    void reloadTasks();
  }, [reloadTasks]);

  /**
   * 创建手动任务并刷新列表，保证页面最终以服务端列表为准。
   */
  const createTask = useCallback(
    async (payload: AutomationTaskCreatePayload) => {
      const token = currentToken();
      if (!token) {
        throw new Error('请先登录后再创建自动化任务');
      }

      setIsSaving(true);
      try {
        await AutomationApi.createTask(token, payload);
        await reloadTasks();
        setErrorMessage('');
      } finally {
        setIsSaving(false);
      }
    },
    [currentToken, reloadTasks],
  );

  /**
   * 编辑任务后重新拉取服务端列表，避免本地乐观更新遗漏后端重新计算的 nextRunAt。
   */
  const updateTask = useCallback(
    async (taskId: string, payload: AutomationTaskUpdatePayload) => {
      const token = currentToken();
      if (!token) {
        throw new Error('请先登录后再编辑自动化任务');
      }

      setIsSaving(true);
      try {
        await AutomationApi.updateTask(token, taskId, payload);
        await reloadTasks();
        setErrorMessage('');
      } finally {
        setIsSaving(false);
      }
    },
    [currentToken, reloadTasks],
  );

  /**
   * 启用或停用任务；调度状态由后端计算后再通过刷新列表回填页面。
   */
  const updateTaskEnabled = useCallback(
    async (taskId: string, enabled: boolean) => {
      const token = currentToken();
      if (!token) {
        throw new Error('请先登录后再管理自动化任务');
      }

      await AutomationApi.updateTaskEnabled(token, taskId, enabled);
      await reloadTasks();
      setErrorMessage('');
    },
    [currentToken, reloadTasks],
  );

  /**
   * 删除任务；后端执行逻辑删除，页面以刷新后的任务列表为准。
   */
  const deleteTask = useCallback(
    async (taskId: string) => {
      const token = currentToken();
      if (!token) {
        throw new Error('请先登录后再删除自动化任务');
      }

      await AutomationApi.deleteTask(token, taskId);
      await reloadTasks();
      setErrorMessage('');
    },
    [currentToken, reloadTasks],
  );

  return useMemo(
    () => ({
      tasks,
      isLoading,
      isSaving,
      errorMessage,
      setErrorMessage,
      reloadTasks,
      createTask,
      updateTask,
      updateTaskEnabled,
      deleteTask,
    }),
    [
      tasks,
      isLoading,
      isSaving,
      errorMessage,
      reloadTasks,
      createTask,
      updateTask,
      updateTaskEnabled,
      deleteTask,
    ],
  );
}
