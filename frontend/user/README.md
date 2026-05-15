# CodingX Frontend

基于 React + Tailwind CSS 构建的前端项目，使用 [Vite+](https://viteplus.dev/guide) 作为统一开发工具链。

## 常用命令

| 命令                | 说明                          |
| ------------------- | ----------------------------- |
| `npm run dev`       | 启动开发服务器，端口为 `5001` |
| `npm run build`     | 生成生产构建                  |
| `npm run preview`   | 预览生产构建结果              |
| `npm run check`     | 运行格式检查、lint 与类型检查 |
| `npm run check:fix` | 自动修复可处理的检查问题      |
| `npm run lint`      | 运行 Vite+ lint 检查          |
| `npm run lint:fix`  | 自动修复可处理的 lint 问题    |
| `npm run fmt`       | 按统一规则格式化代码          |
| `npm run test`      | 运行测试（监听模式）          |
| `npm run test:run`  | 单次运行测试                  |

## 本地启动

1. 安装依赖：`npm install`
2. 按需配置 `GEMINI_API_KEY`
3. 启动开发环境：`npm run dev`

## Vite+ 说明

当前项目已将开发、构建、检查、格式化与测试统一到 `Vite+`：

- `vp dev`：启动开发服务器
- `vp build`：执行生产构建
- `vp preview`：预览构建结果
- `vp check`：执行格式化、lint 与类型检查
- `vp lint`：执行代码规范检查
- `vp fmt`：执行代码格式化
- `vp test`：运行 Vitest 测试

统一配置位于 `vite.config.ts`。
