import React from 'react';
import { Alert, Button, Card, Descriptions, Skeleton, Space, Tag, Typography } from 'antd';
import { useParams } from 'react-router-dom';

import { AdminChatApi, type AdminChatMessageFeedbackDetail } from '../api/adminChatApi';

/**
 * 管理端反馈详情页：使用 Ant Design Descriptions 聚合展示反馈本体与关联消息上下文。
 */
export function FeedbackDetailPage() {
  const { feedbackId = '' } = useParams();
  const [loading, setLoading] = React.useState(false);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [detail, setDetail] = React.useState<AdminChatMessageFeedbackDetail | null>(null);

  React.useEffect(() => {
    if (!feedbackId) {
      return;
    }
    const loadDetail = async () => {
      setLoading(true);
      setErrorMessage('');
      try {
        const data = await AdminChatApi.getFeedbackDetail(feedbackId);
        setDetail(data);
      } catch (error) {
        setErrorMessage(extractErrorMessage(error, '加载反馈详情失败'));
      } finally {
        setLoading(false);
      }
    };
    void loadDetail();
  }, [feedbackId]);

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-wrap items-center justify-between gap-sm">
        <Typography.Title level={2} style={{ margin: 0 }}>
          反馈详情 #{feedbackId || '-'}
        </Typography.Title>
        <Space wrap>
          <Button href="/feedbacks">
            返回列表
          </Button>
          <Button href={`/feedbacks/${feedbackId}/references`} type="primary">
            查看引用来源
          </Button>
        </Space>
      </header>

      {errorMessage ? (
        <Alert showIcon type="error" message={errorMessage} />
      ) : null}

      {loading && !detail ? (
        <Card>
          <Skeleton active paragraph={{ rows: 6 }} />
        </Card>
      ) : null}

      {detail ? (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Card>
            <Descriptions
              bordered
              column={{ xs: 1, md: 2 }}
              items={[
                { key: 'conversationId', label: '会话ID', children: formatValue(detail.conversationId) },
                { key: 'conversationTitle', label: '会话标题', children: formatValue(detail.conversationTitle) },
                { key: 'messageId', label: '消息ID', children: formatValue(detail.messageId) },
                { key: 'messageRole', label: '消息角色', children: formatValue(detail.messageRole) },
                { key: 'vote', label: '反馈投票', children: renderVoteTag(detail.vote) },
                { key: 'reason', label: '反馈原因', children: formatValue(detail.reason) },
                { key: 'comment', label: '反馈评论', children: formatValue(detail.comment) },
              ]}
              size="middle"
            />
          </Card>
          <Card title="关联消息内容">
            <Typography.Paragraph style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
              {detail.messageContent || '-'}
            </Typography.Paragraph>
          </Card>
        </Space>
      ) : null}
    </div>
  );
}

function renderVoteTag(vote?: number) {
  if (vote === 1) {
    return <Tag color="success">点赞</Tag>;
  }
  if (vote === -1) {
    return <Tag color="error">点踩</Tag>;
  }
  return <Tag>-</Tag>;
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return String(value);
}

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error) {
    return error.message || fallback;
  }
  return fallback;
}
