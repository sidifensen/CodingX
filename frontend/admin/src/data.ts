import type { Task, Skill, MCPService, Notification } from './types';

export const mockTasks = [
  {
    id: "TX-9021",
    description: "执行数据清洗脚本 v2.4",
    assignee: "Li Wei (Admin)",
    status: "Running",
    statusLabel: "运行中",
    duration: "12m 4s",
    createdAt: "刚刚"
  },
  {
    id: "TX-8998",
    description: "用户权限组批量更新",
    assignee: "Zhang San",
    status: "Completed",
    statusLabel: "已完成",
    duration: "4m 22s",
    createdAt: "2小时前"
  },
  {
    id: "TX-8842",
    description: "构建主分支容器镜像",
    assignee: "System Root",
    status: "Failed",
    statusLabel: "失败",
    duration: "1m 15s",
    createdAt: "昨天"
  },
  {
    id: "TX-8840",
    description: "清理无效注册用户",
    assignee: "System Root",
    status: "Pending",
    statusLabel: "排队中",
    duration: "-",
    createdAt: "昨天"
  }
];

export const mockSkills = [
  { id: "S-01", name: "Data Extraction", description: "从 PDF 和图像中提取结构化数据。", version: "1.2.0", author: "Core Team" },
  { id: "S-02", name: "Code Reviewer", description: "自动化审查 Pull Request 并提供反馈意见。", version: "2.1.4", author: "DevOps" },
  { id: "S-03", name: "Language Translation", description: "支持 50+ 语言的高效翻译代理。", version: "1.0.0", author: "NLP Group" },
  { id: "S-04", name: "SQL Generator", description: "根据自然语言生成优化后的 SQL 语句。", version: "3.2.1", author: "Core Team" },
  { id: "S-05", name: "UI Agent", description: "通过视觉模型分析并生成界面代码。", version: "0.9.0", author: "Frontend Division" }
];

export const mockMCPServices = [
  { id: "M-01", name: "Local DB Context", endpoint: "localhost:5432", status: "Healthy", statusLabel: "健康", lastChecked: "刚刚" },
  { id: "M-02", name: "Enterprise Directory", endpoint: "ldap.codingx.internal", status: "Degraded", statusLabel: "延迟高", lastChecked: "2分钟前" },
  { id: "M-03", name: "Search Engine Integration", endpoint: "api.search.int", status: "Failed", statusLabel: "无响应", lastChecked: "5分钟前" },
  { id: "M-04", name: "Codebase Indexer", endpoint: "indexer.svc.cluster.local", status: "Healthy", statusLabel: "健康", lastChecked: "刚刚" }
];

export const mockNotifications = [
  { id: "N-01", type: "Alert", title: "数据库连接超时", message: "Node-1 无法连接到主数据库集群，请立即检查服务状态。", createdAt: "10分钟前", isRead: false },
  { id: "N-02", type: "Info", title: "系统维护即将开始", message: "计划于本周六凌晨 2:00 进行系统升级，预计耗时 2 小时。", createdAt: "2小时前", isRead: false },
  { id: "N-03", type: "Success", title: "批量导入完成", message: "成功导入 12,400 条新记录，失败 0 条。", createdAt: "昨天", isRead: true },
  { id: "N-04", type: "Info", title: "新的特性已上线", message: "MCP 管理面板现已支持实时节点监控日志。", createdAt: "昨天", isRead: true }
];
