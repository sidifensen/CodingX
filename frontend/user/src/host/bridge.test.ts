import { describe, expect, it } from 'vitest';
import { resolveHostBridge } from './bridge';

/**
 * 验证宿主桥接在无桌面注入时可回退到 Web 能力。
 */
describe('resolveHostBridge', () => {
  it('应在无注入对象时返回 Web 宿主能力上下文', async () => {
    const bridge = resolveHostBridge();
    const context = await bridge.getContext();

    expect(context.hostType).toBe('web');
    expect(context.executionTargets).toEqual(['cloud']);
    expect(context.capabilities.localFolderPicker).toBe(false);
  });

  it('应优先返回桌面宿主注入的桥接对象', async () => {
    const desktopBridge = {
      getContext: async () => ({
        hostType: 'desktop' as const,
        executionTargets: ['cloud', 'local'] as Array<'cloud' | 'local'>,
        capabilities: {
          localFiles: true,
          localFolderPicker: true,
          shell: true,
          browserAutomation: true,
          desktopNotifications: true,
          officeInterop: true,
          localMcp: true,
        },
        localResource: {
          boundRepositoryPath: 'D:/code/CodingX',
          permissionGranted: true,
        },
      }),
      pickRepositoryDirectory: async () => 'D:/code/CodingX',
      bindRepositoryPath: async () => ({
        hostType: 'desktop' as const,
        executionTargets: ['cloud', 'local'] as Array<'cloud' | 'local'>,
        capabilities: {
          localFiles: true,
          localFolderPicker: true,
          shell: true,
          browserAutomation: true,
          desktopNotifications: true,
          officeInterop: true,
          localMcp: true,
        },
        localResource: {
          boundRepositoryPath: 'D:/code/CodingX',
          permissionGranted: true,
        },
      }),
      requestFileAccess: async () => true,
      listDirectory: async () => [],
    };

    window.codingxHost = desktopBridge;

    const bridge = resolveHostBridge();
    const context = await bridge.getContext();

    expect(bridge).toBe(desktopBridge);
    expect(context.hostType).toBe('desktop');
    expect(context.localResource?.boundRepositoryPath).toBe('D:/code/CodingX');

    delete window.codingxHost;
  });
});
