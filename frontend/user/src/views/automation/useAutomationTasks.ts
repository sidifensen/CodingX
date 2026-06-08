import { useCallback, useEffect, useMemo, useState } from 'react';

import { AutomationApi } from '../../api/automationApi';
import { AuthStorage } from '../../utils/authStorage';
import { AutomationTask, AutomationTaskCreatePayload } from './types';

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

  return useMemo(
    () => ({
      tasks,
      isLoading,
      isSaving,
      errorMessage,
      setErrorMessage,
      reloadTasks,
      createTask,
    }),
    [tasks, isLoading, isSaving, errorMessage, reloadTasks, createTask],
  );
}
