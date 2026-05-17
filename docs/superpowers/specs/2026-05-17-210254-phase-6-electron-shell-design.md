# Phase 6 Electron 宿主与本地资源设计

日期：2026-05-17

## 一、目标与范围

本阶段目标是在不改动现有业务后端接口的前提下，完成以下能力闭环：

- 用 Electron 承载现有 `frontend/user` 页面
- 向前端注入统一宿主能力上下文（Web / Desktop）
- 提供本地仓库目录选择入口
- 提供本地仓库路径绑定入口
- 提供文件访问权限确认入口
- 暴露最小本地资源接口（目录读取）

本阶段明确不做：

- 本地命令执行与本地任务调度
- 本地 MCP 执行桥接
- 云端/本地任务执行路由改造

上述内容进入 `phase-7-electron-local-runtime`。

## 二、约束与设计原则

### 2.1 单前端多宿主

继续保持“同一套 React 前端 + 不同宿主能力”原则。前端通过能力上下文渲染入口，不做页面分叉。

### 2.2 能力显式化

前端依赖 `HostContext` 判断是否展示本地能力入口，避免硬编码 `window.process` 等环境探测。

### 2.3 安全默认

所有本地资源调用必须先走显式确认：

- 用户主动选择目录
- 用户确认文件访问授权

不允许前端直接拼接任意路径访问本地文件系统。

### 2.4 可演进到 Phase 7

本期 IPC 协议保持最小但稳定：字段和方法为 Phase 7 扩展预留，不在本期引入复杂 runtime 语义。

## 三、总体方案

## 3.1 新增 desktop-shell 模块

新增 `desktop-shell/` 独立模块，职责仅包含：

- Electron 主进程（窗口、生命周期、IPC）
- Electron preload（安全暴露桥）
- 宿主本地资源状态维护（内存态）

## 3.2 前端新增 host 能力层

在 `frontend/user/src/host/` 新增：

- `types.ts`：宿主能力与本地资源接口类型
- `bridge.ts`：宿主桥接解析（桌面注入优先，Web fallback）
- `useHostContext.ts`：加载宿主上下文并封装本地资源操作

## 3.3 侧边栏新增宿主与本地资源入口

在 Sidebar 中新增宿主信息区：

- 当前宿主类型（Web / 桌面）
- 当前可用执行目标
- 当支持 `localFolderPicker` 时显示“选择本地仓库目录”按钮
- 显示已绑定仓库路径（若有）

## 四、数据结构与接口

### 4.1 HostContext

```ts
interface HostContext {
  hostType: 'web' | 'desktop';
  executionTargets: ('cloud' | 'local')[];
  capabilities: {
    localFiles: boolean;
    localFolderPicker: boolean;
    shell: boolean;
    browserAutomation: boolean;
    desktopNotifications: boolean;
    officeInterop: boolean;
    localMcp: boolean;
  };
  localResource?: {
    boundRepositoryPath: string | null;
    permissionGranted: boolean;
  };
}
```

### 4.2 Electron 暴露给前端的桥接接口

```ts
interface CodingxHostBridge {
  getContext(): Promise<HostContext>;
  pickRepositoryDirectory(): Promise<string | null>;
  bindRepositoryPath(path: string): Promise<HostContext>;
  requestFileAccess(path: string): Promise<boolean>;
  listDirectory(path: string): Promise<LocalDirectoryEntry[]>;
}
```

### 4.3 IPC 通道

- `host:get-context`
- `host:pick-repository-directory`
- `host:bind-repository-path`
- `host:request-file-access`
- `host:list-directory`

## 五、关键流程

### 5.1 前端启动流程

1. `useHostContext` 调用 `resolveHostBridge()`
2. 若存在 `window.codingxHost`，走桌面桥接
3. 否则走 Web fallback（默认 cloud-only）
4. 把 `HostContext` 下发到 Sidebar

### 5.2 绑定本地仓库流程

1. 用户点击“选择本地仓库目录”
2. 前端调用 `pickRepositoryDirectory`
3. 返回路径后调用 `requestFileAccess`
4. 授权通过则调用 `bindRepositoryPath`
5. 刷新 `HostContext` 并展示绑定路径

### 5.3 失败处理

- 目录选择取消：静默退出，不提示错误
- 授权拒绝：展示中文错误文案
- 路径绑定失败：展示后端/宿主返回文案

## 六、测试策略

### 6.1 逻辑测试

- `resolveHostBridge` 在无桌面注入时返回 Web fallback
- `resolveHostBridge` 在有注入时优先返回桌面桥接对象

### 6.2 组件测试

- 桌面宿主展示本地资源入口并触发选择动作
- Web 宿主隐藏本地资源入口

### 6.3 构建验证

- `frontend/user`: `npm run build`
- `frontend/user`: `npm run test:run`
- `desktop-shell`: `npm run build`

## 七、影响范围

- 新增模块：`desktop-shell`
- 前端改动：`frontend/user` App 壳层与 Sidebar
- 文档改动：`docs/project/plans/phase-6-electron-shell/*`

## 八、验收映射

- AC-001：Electron 可启动并承载现有前端
- AC-002：前端可识别宿主能力并展示对应入口
- AC-003：可完成本地仓库选择、权限确认与路径绑定
- AC-004：Web 宿主下不会展示本地仓库入口
