import { Button, Descriptions, Modal, Typography } from 'antd';

import type { AdminMcpToolView } from '../../api/adminChatApi';

interface McpPingResultModalProps {
  result: AdminMcpToolView | null;
  onClose: () => void;
}

/**
 * MCP 探测结果弹窗：集中展示后端返回消息、耗时和时间。
 */
export function McpPingResultModal({ result, onClose }: McpPingResultModalProps) {
  return (
    <Modal
      open={Boolean(result)}
      title="工具探测结果"
      destroyOnHidden
      transitionName=""
      maskTransitionName=""
      closeIcon={<span aria-label="关闭结果弹窗">×</span>}
      footer={[
        <Button key="close" onClick={onClose}>
          知道了
        </Button>,
      ]}
      onCancel={onClose}
    >
      <Typography.Text code>{result?.toolId ?? '-'}</Typography.Text>
      <Typography.Paragraph style={{ marginTop: 16 }}>
        {result?.message || '无返回消息'}
      </Typography.Paragraph>
      <Descriptions bordered column={1} size="small">
        <Descriptions.Item label="状态">{result?.statusLabel || '-'}</Descriptions.Item>
        <Descriptions.Item label="耗时">
          {typeof result?.durationMs === 'number' ? `${result.durationMs} ms` : '-'}
        </Descriptions.Item>
        <Descriptions.Item label="时间">{result?.checkedAt || '-'}</Descriptions.Item>
      </Descriptions>
    </Modal>
  );
}
