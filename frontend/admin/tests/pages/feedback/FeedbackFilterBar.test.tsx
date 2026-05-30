import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import React from 'react';

import { FeedbackFilterBar } from '@/pages/feedback/FeedbackFilterBar';

describe('FeedbackFilterBar', () => {
  afterEach(() => {
    cleanup();
  });

  it('stages keyword and vote filter until the filter action is submitted', async () => {
    const handleFilter = vi.fn();
    const handleRefresh = vi.fn();

    function FilterHarness() {
      const [keyword, setKeyword] = React.useState('');
      const [vote, setVote] = React.useState<0 | 1 | -1>(0);
      return (
        <FeedbackFilterBar
          keywordValue={keyword}
          voteValue={vote}
          loading={false}
          onKeywordChange={setKeyword}
          onVoteChange={setVote}
          onFilter={handleFilter}
          onRefresh={handleRefresh}
        />
      );
    }

    render(<FilterHarness />);

    fireEvent.change(screen.getByLabelText('关键词'), { target: { value: ' helpful ' } });
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '投票筛选' }));
    fireEvent.click(await screen.findByTitle('仅点赞'));

    expect(handleFilter).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: /筛\s*选/ }));

    expect(handleFilter).toHaveBeenCalledWith({ keyword: 'helpful', vote: 1 });
    expect(handleRefresh).not.toHaveBeenCalled();
    expect(document.querySelector('.ant-form')).toBeInTheDocument();
    expect(document.querySelector('.ant-select')).toBeInTheDocument();
  });
});
