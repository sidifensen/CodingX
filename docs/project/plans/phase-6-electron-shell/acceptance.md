# 阶段验收标准

更新时间：2026-05-17

## 验收项

- AC-001：前端可识别宿主上下文并区分 Web / Desktop
  验收方式：`test`
  通过标准：`src/host/bridge.test.ts` 通过，覆盖 fallback 与注入优先逻辑

- AC-002：桌面宿主展示本地仓库入口，Web 宿主隐藏入口
  验收方式：`test`
  通过标准：`src/components/Sidebar.test.tsx` 通过，验证按钮显示与隐藏行为

- AC-003：本地仓库绑定链路按“选择目录 -> 权限确认 -> 路径绑定”执行
  验收方式：`test`
  通过标准：`useHostContext` 行为由组件测试路径覆盖，点击入口可触发绑定流程

- AC-004：Electron 宿主骨架可编译并暴露白名单 API
  验收方式：`test`
  通过标准：`desktop-shell` 执行 `npm run build` 成功，preload 仅暴露宿主白名单接口

- AC-005：前端改动不破坏既有功能
  验收方式：`test`
  通过标准：`frontend/user` 执行 `npm run test:run` 全量通过（75/75）且 `npm run build` 成功
