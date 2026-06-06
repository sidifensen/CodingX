# 会话导出

## 功能用途

会话导出用于把侧边栏中的单个或多个聊天会话保存到本地文件，便于离线归档、转发和审计。当前用户端支持 Word、PDF、TXT、Json 与 Markdown 格式，其中 PDF 会生成浏览器 PDF 查看器可识别的真实 PDF 文件。

## 使用入口

用户在聊天页左侧会话列表中打开会话菜单，选择“导出对话”后再选择目标格式。前端 `Sidebar` 将会话 ID、格式和分区上下文传给 `App`，`App` 调用 `useChatWorkspace.exportConversation` 或 `exportConversations` 完成导出。

## 核心流程

1. 用户触发导出菜单后，前端将目标会话 ID、导出格式和所属工作空间分区传入 `useChatWorkspace.exportConversations`。导出逻辑先过滤空 ID 并去重，单会话使用会话标题生成文件名，批量导出使用 `批量导出-N-会话` 作为文件名前缀。
2. `resolveConversationExportRecord` 先读取对应分区的本地快照；若快照已有消息、执行步骤、来源或产物，就直接使用本地回放数据，避免不必要的接口请求。若本地快照缺少内容，则用当前登录 token 并发拉取消息、步骤、来源、产物、专家、技能和 MCP，再写回工作空间快照作为后续导出的缓存。
3. `serializeConversationExport` 按格式分支生成下载内容。Json、TXT、Word 和 Markdown 仍分别输出结构化 JSON、纯文本、HTML 文档和 Markdown 文本；PDF 分支会先复用纯文本内容，再生成合法 PDF header、页面对象、内容流、xref、trailer 和 EOF。
4. PDF 内容生成时，文本按近似宽度折行并按页面高度分页，避免长会话全部挤到第一页。真实浏览器环境优先使用 Canvas 将每页文本绘制为 JPEG 并作为 PDF XObject 嵌入，确保中文内容由系统字体渲染；测试环境或 Canvas 不可用时回退到结构化文本 PDF，保证文件仍可被查看器加载。
5. `downloadConversationExport` 使用生成的 Blob 创建 object URL，设置隐藏下载锚点的 `download` 文件名并触发点击；随后通过 `URL.revokeObjectURL` 释放临时 URL。若会话数据获取失败或登录态失效，上层仍按现有错误处理展示后端或统一错误文案。

## 关键文件

- `frontend/user/src/components/Sidebar.tsx`：会话菜单中导出格式入口。
- `frontend/user/src/App.tsx`：把侧边栏导出动作转发给聊天工作区控制器。
- `frontend/user/src/views/chat/useChatWorkspace.ts`：导出数据补齐、格式序列化、PDF 字节生成和浏览器下载触发。
- `frontend/user/tests/views/chat/useChatWorkspace.test.ts`：PDF 导出回归测试，断言生成文件头为 `%PDF-` 且 MIME 为 `application/pdf`。

## 关键数据结构

- `ConversationExportFormat`：用户端导出格式枚举，包含 `word`、`pdf`、`txt`、`json` 与 `markdown`。
- `ConversationExportRecord`：导出时使用的会话快照，包含会话 ID、标题、导出时间、消息、执行步骤、来源、产物及当前专家/技能/MCP。
- `ConversationExportPayload`：序列化后的下载载荷，`content` 使用 `BlobPart[]` 兼容文本格式和 PDF 二进制字节。

## 测试与验证

- `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts -t "导出PDF时应生成合法PDF文件头"`
- `npm run test:run -- tests/views/chat/useChatWorkspace.test.ts`
- `npm run build`
