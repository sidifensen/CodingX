import type { User, Task, Skill, MCPService, Notification } from './types';

export const mockUsers: User[] = [
  {
    id: "1",
    uid: "9527-X-2024",
    name: "张伟 (James Zhang)",
    email: "james.z@codingx.io",
    role: "Admin",
    roleLabel: "管理员",
    status: "Normal",
    lastLogon: "2023-11-20 14:22",
    createdAt: "2023-01-15",
    avatar: "https://lh3.googleusercontent.com/aida-public/AB6AXuAJ_rYm38DfjyzZn7PpggS4AJefkf1-styOOcWjdT7e6oX3w0hcejxZA6Mn-z7HxvkSmEJ2Qzr27iN1sZ5jpwC2oJei_ReXjbIdHj6et4DhohJKt3d7kH2PbecYRPE8CTXQJeF1Atv-QeDYWBfeMB9upfRPXRoLx3VdfjxTJMkkZZdWi-gbrPDixc3nOYynKP_U_ai2BnLqpk4tavx7dYAjyjlhLUMaceHggQWTfdMFSYX8hZY2v4AyRU5HYnaL9fDc9e19RkjeqBU",
  },
  {
    id: "2",
    uid: "9528-X-2024",
    name: "李明 (Ming Li)",
    email: "ming.li@codingx.io",
    role: "Developer",
    roleLabel: "开发者",
    status: "Normal",
    lastLogon: "2023-11-21 09:15",
    createdAt: "2023-03-22",
    avatar: "https://lh3.googleusercontent.com/aida-public/AB6AXuCfcLt8VcquvUaBEiyDZkIAAUpiQJWDnOSAGuXz4XiMxqnNkOZkXS5qZY77r68WEgA2h7DaNeRhlUuzHRDa_Xk0R6o5RAx7ZO5uHEf92LAn8yoHb3OwxfSOtfJtDzeuIP4CPMekaYRdsKMQCo1Nzbkc-qdb9ifJpNDnmxj5rs-jaqr--2Xj2aB8TrwlnrDkR_tDiMHnGi02jodLyIgj7ofTaHSsE3VIkwNRnoD18f-OFcexfMxjEnfQ3vdRkj4y8Bfq6RNSdB2l-h4",
  },
  {
    id: "3",
    uid: "9529-X-2024",
    name: "王芳 (Fang Wang)",
    email: "fang.w@codingx.io",
    role: "Visitor",
    roleLabel: "访客",
    status: "Disabled",
    lastLogon: "2023-10-05 18:30",
    createdAt: "2023-05-10",
    avatar: "https://lh3.googleusercontent.com/aida-public/AB6AXuAxrhY-TY_CSCUmyRpBT3OEbpPEMd-MZGCNGjF2CMTo58owCPoyy_nAzamW2b8HTaDQ9xqAeXoEuQav4r6GYlSmFQLOPKVl1in1ttMZyt6iIAOLFcVkco-7Q-u4VC-VMpzsZj2ORrtJl0rvT2VGSYlldYxNZCaQdzSWcoQVMnryF-ToYwW1TYGVdbzsYcR21wIiGiS_M12LuEu4MPTRUPbdJB1bx1LTYuTgLespGT8eIj1DywcIhflF6VPIDs1QpLOo55DjHmO6rXs",
  },
  {
    id: "4",
    uid: "9530-X-2024",
    name: "周伦 (Lun Zhou)",
    email: "lun.zhou@codingx.io",
    role: "Developer",
    roleLabel: "开发者",
    status: "Pending",
    lastLogon: "-",
    createdAt: "2023-11-21",
    avatar: "https://lh3.googleusercontent.com/aida-public/AB6AXuDwIdTe2LC6kfarBCfTCL9UEU0oiNKqg-mSuzd721hgGm79fWI0aHmQJJRdVLeIbGsGtByRCYfDX933_zIBEX7Lgr214DpKjY2URu7cNR73O-Z2v9Kx_9-hmBLRA4NGCbfaT9ZxCzw-pRdFkfOE4bCajoKx2rWGX2aeBEGyVgmTA3BbeMEK4ciEb0BqiPW3mk0OgM2Xm80TYykLcBkINTziGdTtLEnpsupsmrh5AYm2fdbCiR3FLV7qjZkL79woTPa7UIpYLBw7orQ",
  },
  {
    id: "5",
    uid: "9531-X-2024",
    name: "陈伟 (Chen Wei)",
    email: "chen.wei@codingx.io",
    role: "Developer",
    roleLabel: "高级开发者 (Senior)",
    status: "Normal",
    lastLogon: "2023-11-24 14:30:12",
    createdAt: "2022-05-18",
    avatar: "https://lh3.googleusercontent.com/aida-public/AB6AXuAJbajivkMSvQMKuIsnKgASBSqFj1JMwutZtFPDtlQ62ORyaQVMIq-2xzE00-ib5xD3EJ5X8nARILeG9feBw0OzDPJGblM9bNfOlxRdUTDD4kkh3VBQMZkK38KJC7FIv2bMdmNX-Kdv2OSul3uGHYaItZ3iLj66L9Pk8KbEVqUyZ0wy-2NQCpqJIIgmt88QOY2Hdir3DthBzU2EnRUVKf9Ynytr3jw9AuXV_qsuiF7AoUddvMH8OCvkjBvtTAtTz6TRcniwtXhQSxw",
  }
];

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
