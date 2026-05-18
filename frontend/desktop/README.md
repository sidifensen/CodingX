# CodingX Desktop (Electron 宿主)

本目录用于在本地以 Electron 宿主承载 `frontend/user` 页面，并提供本地能力桥接。

## 目录职责

- `src/main.ts`：Electron 主进程，负责窗口创建、页面加载与 IPC 注册
- `src/preload.ts`：预加载桥接，向渲染层注入 `window.codingxHost`
- `src/types.ts`：宿主上下文与本地目录结构类型定义

## 开发启动

### 方式：分开启动

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

## npm scripts 说明

- `prestart`：执行 `npm run build`，在 `start` 前自动触发
- `build`：执行 `tsc -p tsconfig.json`，编译 TypeScript
- `start`：执行 `electron .`，启动 Electron 宿主
- `dev`：执行 `npm run start`，开发启动别名
- `build:user`：执行 `npm --prefix ../user run build`，构建 `frontend/user` 子项目
- `pack:win`：先构建 desktop 与 user，再执行 `electron-builder --win nsis` 产出 Windows 安装包
- `pack:dir`：先构建 desktop 与 user，再执行 `electron-builder --dir` 产出目录版

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

- `CODINGX_USER_URL`：桌面宿主可选加载地址。
  - 开发态未配置时，默认 `http://localhost:5002`
  - 生产打包态未配置时，默认加载应用内置 `user-dist/index.html`
- 环境加载规则（主进程 `src/main.ts`）：
  - 开发运行（`app.isPackaged === false`）固定按 `development` 读取：`.env.development` → `.env`
  - 打包运行（`app.isPackaged === true`）固定按 `production` 读取：`.env.production` → `.env`
- 开发配置示例：复制 `.env.development.example` 为 `.env.development`，例如：

```bash
CODINGX_USER_URL=http://localhost:5173
```

- 生产配置示例：复制 `.env.production.example` 为 `.env.production`，按需填写 `CODINGX_USER_URL`；若留空则使用打包内置页面。
