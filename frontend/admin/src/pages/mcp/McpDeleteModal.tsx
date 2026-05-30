import { Modal, Typography } from 'antd';

import type { AdminMcpConfig } from '../../api/adminChatApi';

interface McpDeleteModalProps {
  config: AdminMcpConfig | null;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
}

/**
 * MCP 删除确认弹窗：使用 AntD Modal 替代自定义遮罩，保持主题和焦点管理一致。
 */
export function McpDeleteModal({ config, onCancel, onConfirm }: McpDeleteModalProps) {
  return (
    <Modal
      open={Boolean(config)}
      title="删除MCP配置"
      destroyOnHidden
      transitionName=""
      maskTransitionName=""
      okText="确认删除"
      cancelText="取消"
      okButtonProps={{ danger: true }}
      onCancel={onCancel}
      onOk={() => void onConfirm()}
    >
      <Typography.Paragraph style={{ marginBottom: 0 }}>
        确认删除 MCP 配置「{config?.displayName}」吗？删除后用户端将无法选择该 MCP。
      </Typography.Paragraph>
    </Modal>
  );
}
