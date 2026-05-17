# Phase 6 Electron 宿主与本地资源 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. It will decide whether each batch should run in parallel or serial subagent mode and will pass only task-local context to each subagent. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保持现有 Web 业务链路不变的前提下，完成 Electron 宿主骨架与前端本地资源能力入口。

**Architecture:** 新建 `desktop-shell` 独立 Electron 模块，通过 `preload + contextBridge` 暴露最小桥接 API；前端新增 `host` 能力层做宿主识别、能力读取与本地资源操作封装；Sidebar 根据能力上下文显示或隐藏本地资源入口。

**Tech Stack:** Electron、TypeScript、React 19、Vite+、Vitest、Testing Library

---

### Task 1: 先写失败测试（Host Bridge + Sidebar）

**Files:**
- Create: `frontend/user/src/host/bridge.test.ts`
- Create: `frontend/user/src/components/Sidebar.test.tsx`

- [ ] **Step 1: 编写 Host Bridge RED 测试**

```ts
it('无注入时返回 web fallback', async () => {
  const bridge = resolveHostBridge();
  const context = await bridge.getContext();
  expect(context.hostType).toBe('web');
});
```

- [ ] **Step 2: 编写 Sidebar RED 测试**

```tsx
it('desktop 显示本地仓库入口', () => {
  render(<Sidebar {...desktopProps} />);
  expect(screen.getByRole('button', { name: '选择本地仓库目录' })).toBeInTheDocument();
});
```

- [ ] **Step 3: 运行定向测试确认失败**

Run: `npm run test:run -- src/host/bridge.test.ts src/components/Sidebar.test.tsx`
Expected: FAIL，提示 `bridge` 文件缺失或 Sidebar 不存在宿主区域。

- [ ] **Step 4: 提交 RED 测试用例**

```bash
git add frontend/user/src/host/bridge.test.ts frontend/user/src/components/Sidebar.test.tsx
git commit -m "test: 新增宿主桥接与侧栏本地资源入口失败测试"
```

### Task 2: 实现前端 host 能力层（GREEN）

**Files:**
- Create: `frontend/user/src/host/types.ts`
- Create: `frontend/user/src/host/bridge.ts`
- Create: `frontend/user/src/host/useHostContext.ts`
- Modify: `frontend/user/src/types/global.d.ts`（如不存在则创建）

- [ ] **Step 1: 定义宿主上下文与桥接类型**

```ts
export interface HostContext {
  hostType: 'web' | 'desktop';
  executionTargets: ('cloud' | 'local')[];
  capabilities: { ... };
  localResource?: { boundRepositoryPath: string | null; permissionGranted: boolean };
}
```

- [ ] **Step 2: 实现 web fallback 与注入优先桥接解析**

```ts
export function resolveHostBridge(): CodingxHostBridge {
  if (window.codingxHost) return window.codingxHost;
  return createWebFallbackBridge();
}
```

- [ ] **Step 3: 实现 `useHostContext` Hook（含目录选择流程）**

```ts
const pickRepositoryDirectory = async () => {
  const picked = await bridge.pickRepositoryDirectory();
  if (!picked) return;
  const granted = await bridge.requestFileAccess(picked);
  if (!granted) throw new Error('未授予本地文件访问权限');
  const next = await bridge.bindRepositoryPath(picked);
  setHostContext(next);
};
```

- [ ] **Step 4: 运行定向测试确认通过**

Run: `npm run test:run -- src/host/bridge.test.ts`
Expected: PASS

- [ ] **Step 5: 提交 host 能力层实现**

```bash
git add frontend/user/src/host frontend/user/src/types/global.d.ts
git commit -m "feat: 新增前端宿主能力桥接与上下文封装"
```

### Task 3: 接入 App 与 Sidebar（GREEN）

**Files:**
- Modify: `frontend/user/src/App.tsx`
- Modify: `frontend/user/src/components/Sidebar.tsx`
- Modify: `frontend/user/src/components/Sidebar.test.tsx`

- [ ] **Step 1: App 接入 `useHostContext` 并向 Sidebar 传参**

```tsx
const {
  hostContext,
  isLoading: isHostContextLoading,
  errorMessage: hostContextError,
  pickRepositoryDirectory,
} = useHostContext();
```

- [ ] **Step 2: Sidebar 增加宿主信息区与本地资源入口**

```tsx
{hostContext ? (
  <section>
    <div>{hostContext.hostType === 'desktop' ? '桌面宿主' : 'Web 宿主'}</div>
    {hostContext.capabilities.localFolderPicker ? (
      <button aria-label="选择本地仓库目录" onClick={() => void onPickRepositoryDirectory()}>
        选择本地仓库目录
      </button>
    ) : null}
  </section>
) : null}
```

- [ ] **Step 3: 完善桌面路径展示与异常文案展示**

```tsx
{hostContext.localResource?.boundRepositoryPath ? (
  <p>{hostContext.localResource.boundRepositoryPath}</p>
) : (
  <p>尚未绑定本地仓库</p>
)}
```

- [ ] **Step 4: 运行 Sidebar 测试确认通过**

Run: `npm run test:run -- src/components/Sidebar.test.tsx`
Expected: PASS

- [ ] **Step 5: 提交 App/Sidebar 改动**

```bash
git add frontend/user/src/App.tsx frontend/user/src/components/Sidebar.tsx frontend/user/src/components/Sidebar.test.tsx
git commit -m "feat: 侧栏接入宿主识别与本地仓库入口"
```

### Task 4: 新建 desktop-shell Electron 模块

**Files:**
- Create: `desktop-shell/package.json`
- Create: `desktop-shell/tsconfig.json`
- Create: `desktop-shell/src/main.ts`
- Create: `desktop-shell/src/preload.ts`
- Create: `desktop-shell/src/types.ts`

- [ ] **Step 1: 定义 desktop-shell package 脚本与依赖**

```json
{
  "scripts": {
    "dev": "electron-vite dev",
    "build": "tsc -p tsconfig.json",
    "start": "electron ."
  }
}
```

- [ ] **Step 2: 实现主进程窗口与 IPC 处理**

```ts
ipcMain.handle('host:get-context', async () => hostStateToContext());
ipcMain.handle('host:pick-repository-directory', async () => { ... });
ipcMain.handle('host:bind-repository-path', async (_event, path) => { ... });
```

- [ ] **Step 3: preload 通过 contextBridge 暴露白名单 API**

```ts
contextBridge.exposeInMainWorld('codingxHost', {
  getContext: () => ipcRenderer.invoke('host:get-context'),
  pickRepositoryDirectory: () => ipcRenderer.invoke('host:pick-repository-directory'),
  bindRepositoryPath: (path: string) => ipcRenderer.invoke('host:bind-repository-path', path),
  requestFileAccess: (path: string) => ipcRenderer.invoke('host:request-file-access', path),
  listDirectory: (path: string) => ipcRenderer.invoke('host:list-directory', path),
});
```

- [ ] **Step 4: 运行 desktop-shell 构建验证**

Run: `npm run build`
Expected: PASS

- [ ] **Step 5: 提交 desktop-shell**

```bash
git add desktop-shell
git commit -m "feat: 新增 electron desktop-shell 与本地资源 IPC 骨架"
```

### Task 5: 全量验证与阶段文档同步

**Files:**
- Modify: `docs/project/plans/phase-6-electron-shell/tasks.md`
- Modify: `docs/project/plans/phase-6-electron-shell/acceptance.md`
- Modify: `docs/project/plans/phase-6-electron-shell/progress-log.md`
- Modify: `docs/project/plans/phase-6-electron-shell/decisions.md`

- [ ] **Step 1: 运行前端构建**

Run: `npm run build`
Expected: PASS

- [ ] **Step 2: 运行前端测试**

Run: `npm run test:run`
Expected: PASS

- [ ] **Step 3: 运行 desktop-shell 构建**

Run: `npm run build`
Expected: PASS

- [ ] **Step 4: 更新 phase-6 阶段文档**

- `tasks.md` 标记任务状态
- `acceptance.md` 填写 AC 与通过条件
- `progress-log.md` 记录执行结果与问题
- `decisions.md` 记录 Electron 接口边界决策

- [ ] **Step 5: 最终提交**

```bash
git add docs/project/plans/phase-6-electron-shell
git commit -m "docs: 完善 phase-6 阶段任务与验收记录"
```

## Spec coverage quick check

- 宿主识别与 capability 基础层：Task 2 + Task 3
- Electron 宿主壳：Task 4
- 本地目录选择与路径绑定：Task 2 + Task 3 + Task 4
- 权限确认：Task 2 + Task 4
- 阶段文档收口：Task 5

