import { useCallback, useEffect, useMemo, useState } from 'react';
import { resolveHostBridge } from './bridge';
import { HostContext } from './types';
import { AuthStorage } from '../utils/authStorage';
import { ChatApi } from '../views/chat/chatApi';

/**
 * 管理宿主上下文读取与本地仓库绑定交互。
 */
export function useHostContext() {
  const bridge = useMemo(() => resolveHostBridge(), []);
  const [hostContext, setHostContext] = useState<HostContext | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');

  const reloadContext = useCallback(async () => {
    setErrorMessage('');
    const context = await bridge.getContext();
    window.localStorage.setItem('codingx.host.context', JSON.stringify(context));
    setHostContext(context);
    return context;
  }, [bridge]);

  useEffect(() => {
    let cancelled = false;
    const loadContext = async () => {
      setIsLoading(true);
      try {
        const context = await bridge.getContext();
        if (!cancelled) {
          window.localStorage.setItem('codingx.host.context', JSON.stringify(context));
          setHostContext(context);
        }
      } catch (error) {
        if (!cancelled) {
          setErrorMessage(error instanceof Error ? error.message : '读取宿主能力失败');
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void loadContext();

    return () => {
      cancelled = true;
    };
  }, [bridge]);

  const pickRepositoryDirectory = useCallback(async () => {
    setErrorMessage('');
    try {
      const selectedPath = await bridge.pickRepositoryDirectory();
      if (!selectedPath) {
        return;
      }
      const granted = await bridge.requestFileAccess(selectedPath);
      if (!granted) {
        throw new Error('未授予本地文件访问权限');
      }
      const nextContext = await bridge.bindRepositoryPath(selectedPath);
      const token = AuthStorage.getSession()?.token ?? null;
      if (token) {
        await ChatApi.bindWorkspaceRepository(token, selectedPath);
      }
      window.localStorage.setItem('codingx.host.context', JSON.stringify(nextContext));
      setHostContext(nextContext);
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '绑定本地仓库失败');
    }
  }, [bridge]);

  return {
    hostContext,
    isLoading,
    errorMessage,
    reloadContext,
    pickRepositoryDirectory,
  };
}
