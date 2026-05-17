# CodingX Desktop (Electron 宿主)

本目录用于在本地以 Electron 宿主承载 `frontend/user` 前端。

## 目录职责

- `src/main.ts`：Electron 主进程，负责窗口创建与 IPC 注册
- `src/preload.ts`：预加载桥接，向渲染进程注入 `window.codingxHost`
- `src/types.ts`：宿主桥接类型定义

## 启动方式

1. 启动用户前端（端口 `5002`）

```bash
cd D:/code/CodingX/frontend/user
npm run dev
```

2. 启动 Electron 宿主

```bash
cd D:/code/CodingX/frontend/desktop
npm install
npm run start
```

## 可选配置

- `CODINGX_USER_URL`：指定 Electron 加载的用户前端地址，默认 `http://localhost:5002`

Windows PowerShell 示例：

```powershell
$env:CODINGX_USER_URL = "http://localhost:5002"
npm run start
```
