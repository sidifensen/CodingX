export interface Task {
    id: string;
    description: string;
    assignee: string;
    status: 'Running' | 'Completed' | 'Failed' | 'Pending';
    statusLabel: string;
    duration: string;
    createdAt: string;
}

export interface Skill {
  id: string;
  name: string;
  description: string;
  version: string;
  author: string;
}

export interface MCPService {
  id: string;
  name: string;
  endpoint: string;
  status: 'Healthy' | 'Degraded' | 'Failed';
  statusLabel: string;
  lastChecked: string;
}

export interface Notification {
  id: string;
  type: 'Alert' | 'Info' | 'Success';
  title: string;
  message: string;
  createdAt: string;
  isRead: boolean;
}
