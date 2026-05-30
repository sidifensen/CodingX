import type { AdminMcpConfig, AdminMcpToolView } from '../../api/adminChatApi';

export type McpDialogMode = 'create' | 'edit';

export interface McpFormValues {
  mcpCode: string;
  displayName: string;
  description?: string;
  category?: string;
  sourceType?: string;
  sortNo?: number;
  enabled: boolean;
}

export interface UnifiedMcpRow {
  mcpCode: string;
  config: AdminMcpConfig | null;
  tool: AdminMcpToolView | null;
}

export const MCP_TABLE_PAGE_SIZE = 10;
