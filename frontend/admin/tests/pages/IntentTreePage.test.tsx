import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AdminChatApi } from '@/api/adminChatApi';
import { IntentTreePage } from '@/pages/IntentTreePage';

vi.mock('@/api/adminChatApi', () => ({
  AdminChatApi: {
    listIntentTree: vi.fn(),
    listIntents: vi.fn(),
    createIntent: vi.fn(),
    updateIntent: vi.fn(),
    deleteIntent: vi.fn(),
    saveIntent: vi.fn(),
    listTools: vi.fn(),
    createTool: vi.fn(),
    updateTool: vi.fn(),
    deleteTool: vi.fn(),
  },
}));

const intentTreeFixture = [
  {
    id: 'domain-1',
    intentCode: 'biz-root',
    parentCode: '',
    name: '企业知识',
    description: '企业制度与流程',
    intentType: 'search',
    kind: 0,
    level: 0,
    enabled: 1,
    sortNo: 10,
    sortOrder: 10,
    collectionName: 'corp_docs',
    topK: 6,
    examples: JSON.stringify(['企业有哪些报销制度？']),
    promptSnippet: '优先检索制度库',
    children: [
      {
        id: 'topic-1',
        intentCode: 'expense-policy',
        parentCode: 'biz-root',
        name: '报销制度',
        description: '查询差旅与报销规则',
        intentType: 'mcp',
        kind: 2,
        level: 2,
        enabled: 1,
        sortNo: 30,
        sortOrder: 30,
        mcpToolId: 'expense_lookup',
        topK: 3,
        examples: '差旅费怎么报销？\n报销多久到账？',
        children: [],
      },
    ],
  },
] as any;

async function selectAntdOption(label: string, optionText: string, scope: HTMLElement) {
  fireEvent.mouseDown(within(scope).getByLabelText(label));
  fireEvent.click(await screen.findByText(optionText));
}

describe('IntentTreePage', () => {
  beforeEach(() => {
    vi.mocked(AdminChatApi.listIntentTree).mockResolvedValue(intentTreeFixture);
    // 旧接口 mock 用于保证测试能暴露页面未迁移到树接口时的兼容差异。
    vi.mocked(AdminChatApi.listIntents).mockResolvedValue(intentTreeFixture);
    vi.mocked(AdminChatApi.createIntent).mockResolvedValue(intentTreeFixture[0]);
    vi.mocked(AdminChatApi.updateIntent).mockResolvedValue(intentTreeFixture[0]);
    vi.mocked(AdminChatApi.deleteIntent).mockResolvedValue(undefined);
    vi.mocked(AdminChatApi.saveIntent).mockResolvedValue(intentTreeFixture[0]);
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders a left tree and selected node details from the tree API', async () => {
    render(<IntentTreePage />);

    expect(await screen.findByRole('heading', { name: '意图树配置' })).toBeInTheDocument();
    expect(AdminChatApi.listIntentTree).toHaveBeenCalledTimes(1);

    const treePanel = screen.getByRole('region', { name: '意图树结构' });
    const detailPanel = screen.getByRole('region', { name: '节点详情' });

    expect(within(treePanel).getByText('企业知识')).toBeInTheDocument();
    expect(within(treePanel).getByText('DOMAIN')).toBeInTheDocument();
    expect(within(treePanel).getByText('SEARCH')).toBeInTheDocument();
    expect(within(detailPanel).getByText('ROOT')).toBeInTheDocument();
    expect(within(detailPanel).getByText('corp_docs')).toBeInTheDocument();
    expect(within(detailPanel).getByText('企业有哪些报销制度？')).toBeInTheDocument();

    fireEvent.click(within(treePanel).getByRole('button', { name: '收起 企业知识' }));

    expect(within(treePanel).queryByText('报销制度')).not.toBeInTheDocument();
  });

  it('uses independent scroll containers so the desktop page shell does not grow with the tree list', async () => {
    render(<IntentTreePage />);

    await screen.findByRole('region', { name: '意图树结构' });

    const pageShell = screen.getByTestId('intent-tree-page-shell');
    const treeScroll = screen.getByTestId('intent-tree-list-scroll');
    const detailScroll = screen.getByTestId('intent-tree-detail-scroll');

    expect(pageShell).toHaveClass('xl:h-full');
    expect(pageShell).toHaveClass('xl:min-h-0');
    expect(pageShell).toHaveClass('xl:overflow-hidden');
    expect(treeScroll).toHaveClass('xl:flex-1');
    expect(treeScroll).toHaveClass('xl:min-h-0');
    expect(treeScroll).toHaveClass('xl:overflow-y-auto');
    expect(detailScroll).toHaveClass('xl:flex-1');
    expect(detailScroll).toHaveClass('xl:min-h-0');
    expect(detailScroll).toHaveClass('xl:overflow-y-auto');
  });

  it('selects a child node and opens an edit dialog with grouped configuration fields', async () => {
    render(<IntentTreePage />);

    const treePanel = await screen.findByRole('region', { name: '意图树结构' });
    fireEvent.click(within(treePanel).getByRole('button', { name: '选择节点 报销制度' }));

    const detailPanel = screen.getByRole('region', { name: '节点详情' });
    expect(within(detailPanel).getByText('biz-root')).toBeInTheDocument();
    expect(within(detailPanel).getByText('expense_lookup')).toBeInTheDocument();
    expect(within(detailPanel).getByText('差旅费怎么报销？')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '编辑节点' }));

    const dialog = await screen.findByRole('dialog', { name: '编辑意图节点' });
    expect(within(dialog).getByLabelText('节点名称')).toHaveValue('报销制度');
    expect(within(dialog).getByText('基础信息')).toBeInTheDocument();
    expect(within(dialog).getByText('描述与示例')).toBeInTheDocument();
    expect(within(dialog).getByText('Prompt 配置')).toBeInTheDocument();
    expect(within(dialog).getByText('高级设置')).toBeInTheDocument();
  });

  it('switches to cascade columns view and allows selecting child nodes', async () => {
    render(<IntentTreePage />);

    const treePanel = await screen.findByRole('region', { name: '意图树结构' });
    fireEvent.click(within(treePanel).getByRole('button', { name: '分栏级联' }));

    expect(within(treePanel).getByTestId('intent-cascade-columns')).toBeInTheDocument();
    expect(within(treePanel).queryByRole('button', { name: '收起 企业知识' })).not.toBeInTheDocument();

    fireEvent.click(within(treePanel).getByRole('button', { name: '级联选择 报销制度' }));

    const detailPanel = screen.getByRole('region', { name: '节点详情' });
    expect(within(detailPanel).getByText('expense_lookup')).toBeInTheDocument();
    expect(within(detailPanel).getByText('报销制度')).toBeInTheDocument();
  });

  it('validates MCP tool id before creating an MCP node', async () => {
    render(<IntentTreePage />);

    await screen.findByRole('region', { name: '意图树结构' });
    fireEvent.click(screen.getByRole('button', { name: '新建根节点' }));

    const dialog = await screen.findByRole('dialog', { name: '新建意图节点' });
    fireEvent.change(within(dialog).getByLabelText('节点名称'), {
      target: { value: '外部系统查询' },
    });
    fireEvent.change(within(dialog).getByLabelText('意图标识'), {
      target: { value: 'external-tool' },
    });
    await selectAntdOption('节点类型', 'MCP - 工具调用', dialog);
    fireEvent.click(within(dialog).getByRole('button', { name: '创建节点' }));

    expect(await within(dialog).findByText('MCP 类型必须填写 MCP 工具ID')).toBeInTheDocument();
    await waitFor(() => expect(AdminChatApi.createIntent).not.toHaveBeenCalled());
  });

  it('renders delete confirm dialog via body portal to avoid layout clipping by page containers', async () => {
    render(<IntentTreePage />);

    await screen.findByRole('region', { name: '意图树结构' });
    fireEvent.click(screen.getByRole('button', { name: '删除节点' }));

    const pageShell = screen.getByTestId('intent-tree-page-shell');
    const overlay = await screen.findByTestId('intent-delete-dialog-overlay');
    expect(pageShell.contains(overlay)).toBe(false);
    expect(document.body.contains(overlay)).toBe(true);
  });

  it('calls delete API when confirming deletion', async () => {
    render(<IntentTreePage />);

    await screen.findByRole('region', { name: '意图树结构' });
    fireEvent.click(screen.getByRole('button', { name: '删除节点' }));

    const dialog = await screen.findByRole('dialog', { name: '删除意图节点' });
    fireEvent.click(within(dialog).getByRole('button', { name: '确认删除' }));

    await waitFor(() => expect(AdminChatApi.deleteIntent).toHaveBeenCalledWith('domain-1'));
  });
});
