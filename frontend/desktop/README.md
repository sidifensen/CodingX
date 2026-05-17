# CodingX Desktop (Electron 宿主)

本目录用于在本地以 Electron 宿主承载 `frontend/user` 页面，并提供本地能力桥接。

## 目录职责

- `src/main.ts`：Electron 主进程，负责窗口创建、页面加载与 IPC 注册
- `src/preload.ts`：预加载桥接，向渲染层注入 `window.codingxHost`
- `src/types.ts`：宿主上下文与本地目录结构类型定义
- `scripts/dev-all.ps1`：一键联启脚本（自动拉起 `user` 前端后再启动 Electron）

## 开发启动

### 方式 1：一键联启（推荐）

```bash
cd D:/code/CodingX/frontend/desktop
npm install
npm run dev:all
```

### 方式 2：分开启动

1. 启动用户前端（端口 `5002`）

```bash
cd D:/code/CodingX/frontend/user
npm run dev
```

2. 启动 Electron 宿主

```bash
cd D:/code/CodingX/frontend/desktop
npm run start
```

## 打包 Windows EXE

```bash
cd D:/code/CodingX/frontend/desktop
npm install
npm run pack:win
```

- 产物目录：`frontend/desktop/release/`
- 安装包：`release/*.exe`
- 说明：打包时会自动构建 `frontend/user`，并把其 `dist` 作为 `extraResources` 写入 Electron 应用资源。
- 若首次打包失败且报错为下载 `electron-v*.zip` 超时：请先确保机器可访问 GitHub 下载源，或在可联网环境预先缓存 Electron 二进制后重试。

## 可选环境变量

- `CODINGX_USER_URL`：开发态自定义要加载的 user 地址，默认 `http://localhost:5002`
