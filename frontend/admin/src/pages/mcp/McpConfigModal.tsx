import React from 'react';
import { Alert, Form, Input, InputNumber, Modal, Switch } from 'antd';

import type { AdminMcpConfig } from '../../api/adminChatApi';
import type { McpDialogMode, McpFormValues } from './mcpTypes';
import { extractErrorMessage, toMcpFormValues, toMcpPayload } from './mcpUtils';

interface McpConfigModalProps {
  open: boolean;
  mode: McpDialogMode;
  config: AdminMcpConfig | null;
  onCancel: () => void;
  onSubmit: (payload: AdminMcpConfig) => Promise<void>;
}

/**
 * MCP 配置弹窗：使用 AntD Modal/Form 统一新增与编辑交互。
 */
export function McpConfigModal({ open, mode, config, onCancel, onSubmit }: McpConfigModalProps) {
  const [form] = Form.useForm<McpFormValues>();
  const [saving, setSaving] = React.useState(false);
  const [formError, setFormError] = React.useState('');

  React.useEffect(() => {
    if (!open) {
      return;
    }
    setFormError('');
    form.setFieldsValue(toMcpFormValues(config));
  }, [config, form, open]);

  const handleSubmit = async () => {
    setFormError('');
    try {
      const values = await form.validateFields();
      setSaving(true);
      await onSubmit(toMcpPayload(values, config));
    } catch (error) {
      if (isAntdValidationError(error)) {
        return;
      }
      setFormError(extractErrorMessage(error, mode === 'create' ? '新增MCP配置失败' : '保存MCP配置失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title={mode === 'create' ? '新增MCP配置' : '编辑MCP配置'}
      destroyOnHidden
      transitionName=""
      maskTransitionName=""
      okText={mode === 'create' ? '创建配置' : '保存修改'}
      cancelText="取消"
      confirmLoading={saving}
      onCancel={onCancel}
      onOk={() => void handleSubmit()}
    >
      {formError ? (
        <Alert showIcon type="error" message={formError} style={{ marginBottom: 16 }} />
      ) : null}
      <Form<McpFormValues>
        form={form}
        layout="vertical"
        requiredMark={false}
        initialValues={toMcpFormValues(config)}
      >
        <Form.Item label="MCP编码" name="mcpCode" rules={[{ required: true, message: '请输入MCP编码' }]}>
          <Input disabled={mode === 'edit'} />
        </Form.Item>
        <Form.Item label="MCP名称" name="displayName" rules={[{ required: true, message: '请输入MCP名称' }]}>
          <Input />
        </Form.Item>
        <Form.Item label="分类" name="category">
          <Input />
        </Form.Item>
        <Form.Item label="来源类型" name="sourceType">
          <Input />
        </Form.Item>
        <Form.Item label="排序" name="sortNo">
          <InputNumber style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item label="启用状态" name="enabled" valuePropName="checked">
          <Switch checkedChildren="启用" unCheckedChildren="停用" />
        </Form.Item>
        <Form.Item label="MCP说明" name="description">
          <Input.TextArea rows={4} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

function isAntdValidationError(error: unknown): boolean {
  const maybeError = error as { errorFields?: unknown[] } | undefined;
  return Array.isArray(maybeError?.errorFields);
}
