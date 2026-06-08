import { act, renderHook, waitFor } from '@testing-library/react';
import { useChatWorkspace } from '@/views/chat/useChatWorkspace';

/**
 * 覆盖桌面端工作空间切换回归，避免切到新目录后把旧工作空间侧栏分组误清空。
 */
describe('useChatWorkspace workspace switch', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.history.replaceState(window.history.state, '', '/');
    vi.restoreAllMocks();
    window.localStorage.setItem(
      'codingx.auth.session',
      JSON.stringify({
        token: 'token-123',
        userId: '1002',
        username: 'user',
        displayName: 'CodingX User',
        userType: 'USER',
      }),
    );
  });

  /**
   * 切到新的空工作空间后，旧工作空间分组仍应保留在左侧，不能因为新空间的空列表回写而消失。
   */
  it('应在切换到新工作空间后保留旧工作空间分组', async () => {
    window.localStorage.setItem(
      'codingx.chat.workspace.conversations.v1',
      JSON.stringify({
        version: 1,
        snapshots: {
          'local::d:/code/test': {
            runtimeTarget: 'local',
            workspacePath: 'D:/code/test',
            workspaceLabel: 'test',
            lastOpenedAt: Date.now() - 1000,
            activeConversationId: null,
            conversations: [
              {
                id: 'c-test-1',
                title: 'test 会话',
                status: 'ACTIVE',
                workspaceId: '3001',
                workspaceType: 'LOCAL',
              },
            ],
            conversationRecords: {
              'c-test-1': {
                owned: true,
                messages: [],
                executionSteps: [],
                references: [],
                artifacts: [],
                currentExperts: [],
                currentSkills: [],
                currentMcps: [],
              },
            },
            seenTaskFinishedAtByConversationId: {},
          },
        },
      }),
    );

    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.startsWith('/api/chat/conversations?workspaceId=3001')) {
        return jsonResponse({
          items: [
            {
              id: 'c-test-1',
              title: 'test 会话',
              status: 'ACTIVE',
              workspaceId: '3001',
              workspaceType: 'LOCAL',
            },
          ],
          hasMore: false,
          nextCursor: null,
        });
      }
      if (url.startsWith('/api/chat/conversations?workspaceId=3002')) {
        return jsonResponse({
          items: [],
          hasMore: false,
          nextCursor: null,
        });
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return jsonResponse([]);
      }
      throw new Error(`Unhandled fetch in workspace switch regression test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: 'D:/code/test',
        workspaceId: '3001',
        permissionGranted: true,
      },
    };

    const bindWorkspacePath = vi.fn().mockResolvedValue({
      repositoryPath: 'D:/code/new-workspace',
      workspaceId: '3002',
      workspaceName: 'new-workspace',
    });

    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
        bindWorkspacePath,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    expect(result.current.workspaceGroups.map((group) => group.workspaceLabel)).toContain('test');

    await act(async () => {
      await result.current.setActiveWorkspacePath('D:/code/new-workspace');
    });

    await waitFor(() => {
      expect(bindWorkspacePath).toHaveBeenCalledWith('D:/code/new-workspace');
    });

    expect(result.current.workspaceGroups.map((group) => group.workspaceLabel)).toContain('test');
  });

  /**
   * 后端工作区库存中的空本地目录也应进入侧栏，避免管理端可见但用户端不可见。
   */
  it('应展示服务端返回的空本地工作空间分组', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url === '/api/chat/workspaces') {
        return jsonResponse([
          {
            id: '3001',
            name: 'CodingX',
            runtimeTarget: 'local',
            workingDirectory: 'D:/code/CodingX',
            repositoryUrl: null,
            branchName: null,
          },
        ]);
      }
      if (url.startsWith('/api/chat/conversations?workspaceId=3001')) {
        return jsonResponse({
          items: [],
          hasMore: false,
          nextCursor: null,
        });
      }
      if (url.startsWith('/api/chat/conversations?pageSize=30')) {
        return jsonResponse({
          items: [],
          hasMore: false,
          nextCursor: null,
        });
      }
      if (
        url === '/api/chat/sample-questions' ||
        url === '/api/chat/experts' ||
        url === '/api/chat/skills' ||
        url === '/api/chat/mcps'
      ) {
        return jsonResponse([]);
      }
      throw new Error(`Unhandled fetch in server workspace inventory test: ${url}`);
    });

    const hostContext = {
      hostType: 'desktop',
      executionTargets: ['local'] as const,
      capabilities: {
        localFiles: true,
        localFolderPicker: true,
        shell: true,
        browserAutomation: false,
        desktopNotifications: false,
        officeInterop: false,
        localMcp: true,
        windowControls: true,
      },
      localResource: {
        boundRepositoryPath: null,
        workspaceId: null,
        permissionGranted: true,
      },
    };

    const { result } = renderHook(() =>
      useChatWorkspace(true, {
        hostContext,
      }),
    );

    await waitFor(() => {
      expect(result.current.isBootstrapping).toBe(false);
    });
    await waitFor(() => {
      expect(result.current.workspaceGroups.map((group) => group.workspaceLabel)).toContain('CodingX');
    });

    const codingxGroup = result.current.workspaceGroups.find(
      (group) => group.workspaceLabel === 'CodingX',
    );
    expect(codingxGroup).toMatchObject({
      partitionKey: 'local::d:/code/codingx',
      workspacePath: 'D:/code/CodingX',
      runtimeTarget: 'local',
      conversations: [],
    });
  });
});

function jsonResponse(data: unknown) {
  return new Response(
    JSON.stringify({
      success: true,
      code: 'OK',
      message: 'success',
      data,
    }),
    { status: 200 },
  );
}
