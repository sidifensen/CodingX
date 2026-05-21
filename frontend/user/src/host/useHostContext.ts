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
  const [workspaceErrorMessage, setWorkspaceErrorMessage] = useState('');

  const reloadContext = useCallback(async () => {
    setErrorMessage('');
    const context = await bridge.getContext();
    window.localStorage.setItem('codingx.host.context', JSON.stringify(context));
    setHostContext(context);
    return context;
  }, [bridge]);

  /**
   * 将宿主侧本地仓库路径同步到后端工作区绑定接口，保证聊天执行链路能复用该目录。
   * @param repositoryPath 本地仓库路径。
   */
  const syncWorkspaceBinding = useCallback(async (repositoryPath: string) => {
    setWorkspaceErrorMessage('');
    const token = AuthStorage.getSession()?.token ?? null;
    if (!token) {
      return;
    }
    try {
      return await ChatApi.bindWorkspaceRepository(token, repositoryPath);
    } catch (error) {
      setWorkspaceErrorMessage(error instanceof Error ? error.message : '同步工作空间失败');
      return null;
    }
  }, []);

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
        return null;
      }
      const granted = await bridge.requestFileAccess(selectedPath);
      if (!granted) {
        throw new Error('未授予本地文件访问权限');
      }
      const bindingResult = await syncWorkspaceBinding(selectedPath);
      const nextContext = await bridge.bindRepositoryPath(selectedPath, {
        workspaceId: bindingResult?.workspaceId,
        workspaceName: bindingResult?.workspaceName,
      });
      window.localStorage.setItem('codingx.host.context', JSON.stringify(nextContext));
      setHostContext(nextContext);
      return selectedPath;
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : '绑定本地仓库失败');
      return null;
    }
  }, [bridge, syncWorkspaceBinding]);

  /**
   * 直接切换到指定本地仓库路径，供工作空间列表点击切换时复用。
   * @param repositoryPath 本地仓库路径。
   */
  const bindWorkspacePath = useCallback(
    async (repositoryPath: string) => {
      setErrorMessage('');
      try {
        const bindingResult = await syncWorkspaceBinding(repositoryPath);
        const nextContext = await bridge.bindRepositoryPath(repositoryPath, {
          workspaceId: bindingResult?.workspaceId,
          workspaceName: bindingResult?.workspaceName,
        });
        window.localStorage.setItem('codingx.host.context', JSON.stringify(nextContext));
        setHostContext(nextContext);
        return bindingResult ?? null;
      } catch (error) {
        setErrorMessage(error instanceof Error ? error.message : '切换工作空间失败');
        return null;
      }
    },
    [bridge, syncWorkspaceBinding],
  );

  return {
    hostContext,
    isLoading,
    errorMessage,
    workspaceErrorMessage,
    reloadContext,
    pickRepositoryDirectory,
    bindWorkspacePath,
    syncWorkspaceBinding,
  };
}
