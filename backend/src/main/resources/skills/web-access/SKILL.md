---
name: web-access
license: MIT
github: https://github.com/eze-is/web-access
description: >
  所有联网操作必须通过此 skill 处理，包括：搜索、网页抓取、登录后操作、网络交互等。
  触发场景：用户要求搜索信息、查看网页内容、访问需要登录的网站、操作网页界面、抓取社交媒体内容，以及任何需要真实浏览器环境的网络任务。
metadata:
  author: 一泽Eze
  version: "2.4.3"
---

# web-access Skill

## 前置检查

在开始联网操作前，先检查 CDP 模式可用性：

```bash
node "${CLAUDE_SKILL_DIR}/scripts/check-deps.mjs"
```

未通过时引导用户完成设置：

- **Node.js 22+**：必需；版本低于 22 时可用性下降，需安装 `ws` 模块
- **Chrome remote-debugging**：在 Chrome 地址栏打开 `chrome://inspect/#remote-debugging`，勾选 **Allow remote debugging for this browser instance**

检查通过后，再启动 CDP Proxy 执行操作。

> 温馨提示：部分站点对浏览器自动化操作检测严格，存在账号封禁风险。已内置防护措施但无法完全避免，Agent 继续操作即视为接受。

## 浏览哲学

像人一样思考，兼顾高效与适应性的完成任务。

执行任务时不会过度依赖固有印象所规划的步骤，而是带着目标进入，边看边判断，遇到阻碍就解决，发现内容不够就深入。全程围绕「我要达成什么」做决策。

## 联网工具选择

| 场景 | 工具 |
|------|------|
| 搜索摘要或关键词结果，发现信息来源 | WebSearch |
| URL 已知，需要从页面定向提取特定信息 | WebFetch |
| URL 已知，需要原始 HTML 源码 | curl |
| 非公开内容，或静态层无效的平台 | 浏览器 CDP |
| 需要登录态、交互操作，或需要像人一样在浏览器内自由导航 | 浏览器 CDP |

## 浏览器 CDP 模式

通过 CDP Proxy 直连用户日常 Chrome，天然携带登录态，无需启动独立浏览器。若无用户明确要求，不主动操作用户已有 tab。

### 本项目内置工具

在 CodingX 中，上述能力已经封装为模型可见工具 `web_access`，默认对接 `http://127.0.0.1:3456`。

调用时请传入 JSON 参数，常见动作如下：

```json
{"action":"targets"}
{"action":"new","url":"https://example.com"}
{"action":"eval","target":"TAB_ID","script":"document.title"}
{"action":"screenshot","target":"TAB_ID"}
{"action":"click","target":"TAB_ID","selector":"button.submit"}
{"action":"setFiles","target":"TAB_ID","selector":"input[type=file]","files":["/path/to/file.png"]}
{"action":"scroll","target":"TAB_ID","direction":"bottom"}
{"action":"close","target":"TAB_ID"}
```

关键约束：

- `targets` 用于列出当前标签页
- `new` 用于打开新标签页，`url` 必填
- `eval`、`screenshot`、`click`、`setFiles`、`scroll`、`close` 需要先拿到 `target`
- `setFiles` 的 `files` 必须是本地文件绝对路径列表
- 截图默认会落到本机临时目录，便于后续再用 `view_image` 检查

### 常用操作

```bash
curl -s http://localhost:3456/targets
curl -s "http://localhost:3456/new?url=https://example.com"
curl -s -X POST "http://localhost:3456/eval?target=ID" -d 'document.title'
curl -s "http://localhost:3456/screenshot?target=ID&file=/tmp/shot.png"
curl -s -X POST "http://localhost:3456/click?target=ID" -d 'button.submit'
curl -s -X POST "http://localhost:3456/setFiles?target=ID" -d '{"selector":"input[type=file]","files":["/path/to/file.png"]}'
curl -s "http://localhost:3456/scroll?target=ID&direction=bottom"
curl -s "http://localhost:3456/close?target=ID"
```

## 任务结束

用 `/close` 关闭自己创建的 tab，保留用户原有的 tab 不受影响。
