import {
  Blocks,
  Bot,
  BriefcaseBusiness,
  ListTodo,
  Orbit,
  Sparkles,
} from 'lucide-react';

/**
 * Declares the canonical navigation entries rendered by the shell.
 */
export const shellNavigation = [
  {to: '/tasks', label: '任务', icon: ListTodo},
  {to: '/experts', label: '专家', icon: Bot},
  {to: '/skills', label: '技能', icon: Sparkles},
  {to: '/tools', label: '工具', icon: BriefcaseBusiness},
  {to: '/mcp', label: 'MCP', icon: Blocks},
  {to: '/automations', label: '自动化', icon: Orbit},
] as const;
