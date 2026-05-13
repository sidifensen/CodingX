import {describe, expect, it} from 'vite-plus/test';
import {fireEvent, render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import App from '../App';
import '@testing-library/jest-dom/vitest';

/**
 * Verifies the shell navigation and default routing behavior.
 */
describe('app shell', () => {
  /**
   * Confirms the primary navigation links are rendered.
   */
  it('shows the formal navigation entries', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App withRouter={false} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', {name: '任务'})).toBeInTheDocument();
    expect(screen.getByRole('link', {name: '专家'})).toBeInTheDocument();
    expect(screen.getByRole('link', {name: '技能'})).toBeInTheDocument();
    expect(screen.getByRole('link', {name: '工具'})).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'MCP'})).toBeInTheDocument();
    expect(screen.getByRole('link', {name: '自动化'})).toBeInTheDocument();
  });

  /**
   * Confirms the tasks page is the default landing route.
   */
  it('renders the task page by default', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App withRouter={false} />
      </MemoryRouter>,
    );

    expect(screen.getByText('任务工作台')).toBeInTheDocument();
  });

  /**
   * Confirms the create-task entry navigates to the new task placeholder page.
   */
  it('navigates to a blank new-task page from the create button', () => {
    render(
      <MemoryRouter initialEntries={['/tasks']}>
        <App withRouter={false} />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole('link', {name: '新建任务'}));

    expect(screen.getByRole('heading', {name: '新建任务'})).toBeInTheDocument();
    expect(screen.getByText('从这里开始输入新的任务目标。')).toBeInTheDocument();
    expect(screen.queryByText('最近任务')).not.toBeInTheDocument();
  });
});
