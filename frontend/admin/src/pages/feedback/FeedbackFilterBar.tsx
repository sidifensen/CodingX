import React from 'react';
import { Button, Form, Input, Select, Space } from 'antd';

export type FeedbackVoteFilter = 0 | 1 | -1;

interface FeedbackFilterPayload {
  keyword: string;
  vote: FeedbackVoteFilter;
}

interface FeedbackFilterBarProps {
  keywordValue: string;
  voteValue: FeedbackVoteFilter;
  loading: boolean;
  onKeywordChange: (value: string) => void;
  onVoteChange: (value: FeedbackVoteFilter) => void;
  onFilter: (payload: FeedbackFilterPayload) => void;
  onRefresh: () => void;
}

/**
 * 反馈筛选栏：只维护待应用条件，点击筛选时再把条件提交给页面级查询状态。
 */
export function FeedbackFilterBar({
  keywordValue,
  voteValue,
  loading,
  onKeywordChange,
  onVoteChange,
  onFilter,
  onRefresh,
}: FeedbackFilterBarProps) {
  const handleSubmit = React.useCallback(() => {
    if (loading) {
      return;
    }
    onFilter({
      keyword: keywordValue.trim(),
      vote: voteValue,
    });
  }, [keywordValue, loading, onFilter, voteValue]);

  return (
    <Form layout="inline" className="ant-feedback-filter-form">
      <Form.Item label="关键词">
        <Input
          allowClear
          aria-label="关键词"
          placeholder="原因或评论"
          style={{ width: 220 }}
          value={keywordValue}
          onChange={(event) => onKeywordChange(event.target.value)}
          onPressEnter={handleSubmit}
        />
      </Form.Item>
      <Form.Item label="投票筛选">
        <Select
          aria-label="投票筛选"
          options={[
            { label: '全部', value: 0 },
            { label: '仅点赞', value: 1 },
            { label: '仅点踩', value: -1 },
          ]}
          style={{ width: 140 }}
          value={voteValue}
          onChange={(value) => onVoteChange(value as FeedbackVoteFilter)}
        />
      </Form.Item>
      <Form.Item>
        <Space>
          <Button loading={loading} type="primary" onClick={handleSubmit}>
            筛选
          </Button>
          <Button disabled={loading} onClick={onRefresh}>
            刷新
          </Button>
        </Space>
      </Form.Item>
    </Form>
  );
}
