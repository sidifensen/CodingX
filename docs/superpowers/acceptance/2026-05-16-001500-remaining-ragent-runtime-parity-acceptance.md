# Remaining Ragent Runtime Parity Acceptance

## 范围

本轮验收覆盖：搜索管道化、文件存储抽象、管理后台页面/API、token/响应清洗/线程池治理、thinking 展示与停止生成、欢迎屏 CDP 视觉证据。

## 验收标准

1. 搜索管道
- 至少两个搜索通道可并行执行。
- 搜索结果会经过去重和 rerank/截断后再进入来源回放。
- 搜索步骤、来源和产物链路保持兼容。

2. 文件存储抽象
- `DocumentArtifactService` 不再手写本地路径。
- 本地文件存储实现可真实写入并返回 `storagePath`。

3. 后台 API
- Trace 列表/详情接口可用。
- 意图树管理接口可查询和保存。
- 关键词映射管理接口可查询和保存。
- 系统设置接口可查询和更新。
- Dashboard 接口返回真实聚合数据。

4. 管理端页面
- 管理端出现 Dashboard、Trace、意图树、关键词映射、系统设置入口。
- 页面不是静态 mock，能消费后端真实接口。
- 深色/浅色模式下可读。

5. 用户前端体验
- SSE `thinking` 事件可展示。
- 停止生成按钮在流式过程中可用并有正确状态过渡。
- 欢迎屏示例问题与输入区在浏览器中视觉可读。

6. 运行时治理
- token 预算可在请求构造前给出估算。
- 模型返回文本会经过响应清洗。
- 搜索 / MCP / 聊天入口线程池分离。

7. 验证
- `backend`: `mvn compile`, `mvn test`
- `frontend/user`: `npm run test:run`, `npm run build`
- `frontend/admin`: `npm run test:run`, `npm run build`
- 至少完成用户端和管理端各一次 CDP 截图/视觉证据
