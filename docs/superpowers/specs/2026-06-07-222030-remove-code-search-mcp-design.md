# Remove Code Search MCP Design

## Background

管理端意图树中仍存在“代码检索与定位”根节点和“代码查找”叶子节点，叶子节点绑定内置 MCP `code_search`。当前前端删除按钮可以正常发起 `DELETE /api/admin/chat/intents/3292`，但用户目标是下线整套代码检索能力；仅删除意图叶子不会移除 MCP 工具、运行时设置和后端执行器，删除父节点又会被子节点保护规则拦截。

## Goal

完整移除代码检索能力，使新环境、迁移后的既有环境、管理端意图树、MCP 管理、系统设置和聊天运行时都不再暴露 `code`、`code-search`、`code_search`。

## Scope

1. 数据层删除 `chat_intent_node` 中 `code` 子树、`mcp` 中 `code_search` 配置、`setting` 中 `code_search.*` 运行时配置。
2. 基线初始化 `db/init.sql` 不再写入代码检索意图、MCP 和运行时设置。
3. 后端删除 `CodeSearchMcpToolExecutor`，并清理 `RuntimeProperties` 与 `RuntimeSettingService` 中只服务于代码检索的读取方法。
4. 后端测试从“代码检索存在并可执行”改为“代码检索已下线且种子脚本不再回填”。
5. 管理端设置页不再展示“代码检索运行时”分类文案；MCP/技能测试中的 `code_search` mock 数据改为其他保留能力。

## Data Flow

1. 应用迁移启动时，新迁移先递归选中 `chat_intent_node.intent_code IN ('code', 'code-search')` 的节点，再删除该子树。随后删除 `mcp.mcp_code = 'code_search'` 和 `setting.setting_key LIKE 'code_search.%'` 或 `category_code = 'code_search'` 的记录，避免旧环境继续展示入口或配置项。
2. 新环境执行 `db/init.sql` 时，不再插入 `code` / `code-search` 意图、`code_search` MCP 和 `code_search.*` 设置。初始化后的管理端列表只能看到保留的联网搜索、系统交互、天气查询等能力。
3. 聊天运行时组装 MCP 执行器时，Spring 容器不再注册 `CodeSearchMcpToolExecutor`。即使旧对话或用户输入携带 `code_search`，`ChatMcpExecutionService` 也无法从配置与注册表获得可用工具，会走既有“未找到/未配置工具”的中文错误兜底。
4. 管理端页面加载意图树、MCP 列表和设置分类时，后端数据源中已无代码检索记录，前端也不再通过硬编码标题重新引入“代码检索运行时”字样。

## Error Handling

- 删除迁移使用精确编码条件，只影响 `code`、`code-search`、`code_search` 和 `code_search.*`，不会清理天气、联网搜索、系统交互或本地工具能力。
- 运行时若遇到历史请求仍引用 `code_search`，沿用现有工具缺失异常链路，由全局异常处理器返回中文 `ApiResponse.message`。
- 设置项清理同时匹配 `setting_key` 和 `category_code`，兼容历史版本中只写入分类或键名前缀的记录。

## Testing

- 后端种子脚本测试断言 `init.sql` 和新迁移不再包含代码检索保留标记，并断言清理迁移覆盖意图、MCP 和设置。
- 后端 MCP 流程测试保留天气 MCP 正常执行，删除代码检索专用执行测试和代码检索聊天流测试。
- 管理端页面测试使用天气或其他保留能力替代 `code_search` mock，验证列表和页面仍能渲染。
- 浏览器验证在 `http://localhost:5003/intent-tree` 和 MCP/设置相关页面确认不再出现代码检索节点、工具或配置分类。
