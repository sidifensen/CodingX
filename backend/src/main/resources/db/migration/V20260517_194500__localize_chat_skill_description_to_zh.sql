-- 将已导入的第三方技能描述统一中文化，避免管理端出现中英混杂。
UPDATE chat_skill
SET description = CASE skill_code
    WHEN 'agent-deep-links' THEN '构建、校验并排查 Codex、Cursor、VS Code、Visual Studio 等工具的深链接，适用于在 Slack 等场景通过可点击链接打开会话、文件、文件夹或应用设置'
    WHEN 'brand-guidelines' THEN '将 OpenAI 品牌色与排版规范应用到内容产物，适用于需要遵循公司视觉标准与品牌风格的场景'
    WHEN 'changelog-generator' THEN '基于 Git 提交历史自动生成面向用户的更新日志，完成变更归类并输出清晰易读的发布说明'
    WHEN 'codebase-migrate' THEN '执行大规模代码库迁移与多文件重构，通过 Composio CLI 协调批量 PR 与 CI 校验，支持一次处理大量文件'
    WHEN 'content-research-writer' THEN '通过调研、补充引用、优化提纲与分段反馈，协助产出高质量内容，提升写作效率与成稿质量'
    WHEN 'create-plan' THEN '生成简洁执行计划，适用于用户明确要求先为编码任务输出计划的场景'
    WHEN 'datadog-logs' THEN '通过 Composio CLI 在命令行查询和过滤 Datadog 日志，支持按服务和环境检索并导出结构化 JSON'
    WHEN 'deploy-pipeline' THEN '通过 Composio CLI 执行 Stripe、Supabase、Vercel 端到端发布流水线，并完成发布后校验'
    WHEN 'email-draft-polish' THEN '按目标语气、长度和受众起草、改写或精简邮件，适用于外联、回复、状态同步与升级沟通'
    WHEN 'file-organizer' THEN '基于上下文智能整理本地文件与文件夹，识别重复、建议结构并自动清理，保持工作区整洁'
    WHEN 'gh-address-comments' THEN '使用 gh CLI 处理当前分支 GitHub PR 的评审与问题评论，执行前先校验 gh 登录状态'
    WHEN 'gh-fix-ci' THEN '使用 gh 检查 PR 状态并拉取 GitHub Actions 失败日志，先总结上下文，再在确认后规划并实施修复'
    WHEN 'image-enhancer' THEN '提升图片尤其截图的清晰度、锐度与分辨率，适用于演示文档、技术文档和社媒素材'
    WHEN 'internal-comms' THEN '提供企业常用模板与写作框架，支持状态报告、管理层更新、FAQ、事故复盘和项目进展等内部沟通'
    WHEN 'invoice-organizer' THEN '自动整理报税所需发票与收据，提取关键信息、统一命名并按规则归档，降低手工整理成本'
    WHEN 'issue-triage' THEN '通过 Composio CLI 分诊 Linear 或 Jira 待办与缺陷池，支持批量拉取、去重、重标记、重分配与汇总输出'
    WHEN 'langsmith-fetch' THEN '通过抓取 LangSmith Studio 执行轨迹调试 LangChain 与 LangGraph Agent，用于定位错误和分析调用链路'
    WHEN 'lead-research-assistant' THEN '结合业务画像与目标企业检索能力识别高质量销售线索，并提供可执行联系策略'
    WHEN 'mcp-builder' THEN '用于构建高质量 MCP 服务，帮助 LLM 通过工具接入外部 API 或系统，支持 Python FastMCP 与 Node/TypeScript SDK'
    WHEN 'meeting-insights-analyzer' THEN '分析会议转录与录音，识别沟通模式并给出可执行反馈，帮助提升沟通与领导力'
    WHEN 'meeting-notes-and-actions' THEN '将会议转录或零散笔记整理为摘要，输出决策、风险与责任人行动项，便于直接分发跟进'
    WHEN 'notion-knowledge-capture' THEN '将对话与决策沉淀为结构化 Notion 页面，适用于知识库条目、操作指南、决策记录与 FAQ 整理'
    WHEN 'notion-research-documentation' THEN '在 Notion 多来源内容中检索并整合结构化文档，生成带引用的简报、对比分析与研究报告'
    WHEN 'openai-docs' THEN '当用户咨询 OpenAI 产品或 API 开发且需要最新官方文档与引用时使用，优先调用 OpenAI Docs MCP 工具'
    WHEN 'pdf' THEN '适用于阅读、生成或评审对版式敏感的 PDF 任务，优先可视化校验并结合 Python 工具完成生成与提取'
    WHEN 'playwright' THEN '当任务需要终端自动化真实浏览器时使用，支持导航、表单、截图、数据采集与 UI 流程排查'
    WHEN 'pr-review-ci-fix' THEN '基于 Composio CLI 自动化执行 PR 评审与 CI 修复，拉取差异与失败日志并循环修复直至检查通过'
    WHEN 'screenshot' THEN '当用户明确要求桌面或系统截图，或工具级截图不可用时，使用系统级截图能力完成采集'
    WHEN 'sentry-triage' THEN '通过 Composio CLI 拉取 Sentry 问题详情、事件与可疑提交，并映射到本地源码以支持快速修复'
    WHEN 'skill-creator' THEN '用于创建或更新高质量技能定义，扩展 Codex 在专业知识、工作流和工具集成方面的能力'
    WHEN 'skill-installer' THEN '将技能从精选清单或 GitHub 仓库安装到 CODEX_HOME 技能目录，支持精选与外部仓库安装'
    WHEN 'spreadsheet-formula-helper' THEN '编写与调试 Excel 和 Google Sheets 公式、透视与数组公式，支持方言互转与边界校验'
    WHEN 'support-ticket-triage' THEN '将客服工单、邮件或聊天分诊为分类、优先级与下一步动作，并生成回复草稿与复现步骤'
    WHEN 'webapp-testing' THEN '基于 Playwright 的本地 Web 应用测试工具包，支持功能校验、UI 排查、截图采集与日志查看'
    WHEN 'ops-summary' THEN '汇总运营指标并生成可执行结论'
    ELSE description
END,
updated_at = CURRENT_TIMESTAMP
WHERE deleted = 0
  AND skill_code IN (
    'agent-deep-links',
    'brand-guidelines',
    'changelog-generator',
    'codebase-migrate',
    'content-research-writer',
    'create-plan',
    'datadog-logs',
    'deploy-pipeline',
    'email-draft-polish',
    'file-organizer',
    'gh-address-comments',
    'gh-fix-ci',
    'image-enhancer',
    'internal-comms',
    'invoice-organizer',
    'issue-triage',
    'langsmith-fetch',
    'lead-research-assistant',
    'mcp-builder',
    'meeting-insights-analyzer',
    'meeting-notes-and-actions',
    'notion-knowledge-capture',
    'notion-research-documentation',
    'openai-docs',
    'pdf',
    'playwright',
    'pr-review-ci-fix',
    'screenshot',
    'sentry-triage',
    'skill-creator',
    'skill-installer',
    'spreadsheet-formula-helper',
    'support-ticket-triage',
    'webapp-testing',
    'ops-summary'
  );
