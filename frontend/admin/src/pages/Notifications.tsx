import React, { useState } from 'react';
import { BellOutlined, CheckCircleOutlined, InfoCircleOutlined, WarningOutlined } from '@ant-design/icons';
import { Avatar, Badge, Button, Empty, List, Space, Typography } from 'antd';
import { mockNotifications, Notification } from '../data';

/**
 * 通知中心：展示系统通知并支持逐条或批量标记已读。
 */
export function Notifications() {
  const [notifications, setNotifications] = useState<Notification[]>(mockNotifications);

  const markAsRead = (id: string) => {
    setNotifications((prev) => prev.map((item) => item.id === id ? { ...item, isRead: true } : item));
  };

  const markAllAsRead = () => {
    setNotifications((prev) => prev.map((item) => ({ ...item, isRead: true })));
  };

  const unreadCount = notifications.filter((item) => !item.isRead).length;

  return (
    <div className="w-full space-y-lg p-lg">
      <header className="flex flex-col gap-sm lg:flex-row lg:items-end lg:justify-between">
        <div>
          <Space>
            <BellOutlined />
            <Typography.Title level={2} style={{ margin: 0 }}>通知中心</Typography.Title>
          </Space>
          <Typography.Text type="secondary">系统告警、任务完成通知及团队消息协作。</Typography.Text>
        </div>
        {unreadCount > 0 ? (
          <Button icon={<CheckCircleOutlined />} type="primary" onClick={markAllAsRead}>
            全部标记为已读
          </Button>
        ) : null}
      </header>

      <List
        dataSource={notifications}
        locale={{ emptyText: <Empty description="暂无通知" /> }}
        renderItem={(note) => (
          <List.Item
            className="rounded-xl border border-border-hairline bg-surface-container-lowest px-md"
            actions={!note.isRead ? [
              <Button key="read" icon={<CheckCircleOutlined />} size="small" onClick={() => markAsRead(note.id)}>
                标记已读
              </Button>,
            ] : undefined}
            onClick={() => markAsRead(note.id)}
          >
            <List.Item.Meta
              avatar={(
                <Badge dot={!note.isRead}>
                  <Avatar icon={toNotificationIcon(note.type)} />
                </Badge>
              )}
              title={<Typography.Text type={note.isRead ? 'secondary' : undefined}>{note.title}</Typography.Text>}
              description={(
                <div>
                  <Typography.Paragraph className="mb-1" ellipsis={{ rows: 2 }} type="secondary">
                    {note.message}
                  </Typography.Paragraph>
                  <Typography.Text type="secondary">{note.createdAt}</Typography.Text>
                </div>
              )}
            />
          </List.Item>
        )}
      />
    </div>
  );
}

function toNotificationIcon(type: Notification['type']) {
  if (type === 'Alert') {
    return <WarningOutlined />;
  }
  if (type === 'Info') {
    return <InfoCircleOutlined />;
  }
  return <CheckCircleOutlined />;
}
