import React from 'react';
import {
  CloseOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  FolderAddOutlined,
  PlusOutlined,
  ReloadOutlined,
  SaveOutlined,
} from '@ant-design/icons';
import { Alert, Button, Empty, Form, Input, Modal, Select, Space, Spin, Switch, Tag, Tree, Typography } from 'antd';
import type { DataNode } from 'antd/es/tree';

import { AdminChatApi, AdminIntentNode } from '../api/adminChatApi';
import { useAdminMessage } from '../components/AdminMessageContext';

const ROOT_PARENT = '__ROOT__';

const LEVEL_OPTIONS = [
  { value: 0, label: 'DOMAIN', description: '顶层领域' },
  { value: 1, label: 'CATEGORY', description: '业务分类' },
  { value: 2, label: 'TOPIC', description: '具体主题' },
];

const KIND_OPTIONS = [
  { value: 0, label: 'SEARCH', description: '联网检索' },
  { value: 1, label: 'SYSTEM', description: '系统交互' },
  { value: 2, label: 'MCP', description: '工具调用' },
];

type DialogMode = 'create' | 'edit';
type IntentTreeViewMode = 'tree' | 'cascade';

interface IntentFormState {
  intentCode: string;
  name: string;
  parentCode: string;
  level: string;
  kind: string;
  searchScopeId: string;
  collectionName: string;
  topK: string;
  sortOrder: string;
  enabled: boolean;
  description: string;
  examplesText: string;
  promptSnippet: string;
  promptTemplate: string;
  mcpToolId: string;
  paramPromptTemplate: string;
}

interface TreeOption {
  label: string;
  value: string;
}

const emptyForm: IntentFormState = {
  intentCode: '',
  name: '',
  parentCode: ROOT_PARENT,
  level: '0',
  kind: '0',
  searchScopeId: '',
  collectionName: '',
  topK: '',
  sortOrder: '0',
  enabled: true,
  description: '',
  examplesText: '',
  promptSnippet: '',
  promptTemplate: '',
  mcpToolId: '',
  paramPromptTemplate: '',
};

function resolveKind(node: AdminIntentNode): number {
  if (typeof node.kind === 'number') {
    return node.kind;
  }
  if (node.intentType === 'mcp') {
    return 2;
  }
  if (node.intentType === 'system') {
    return 1;
  }
  return 0;
}

function resolveIntentType(kind: number): string {
  if (kind === 2) {
    return 'mcp';
  }
  if (kind === 1) {
    return 'system';
  }
  return 'search';
}

function resolveLevelLabel(value?: number): string {
  return LEVEL_OPTIONS.find((option) => option.value === (value ?? 0))?.label ?? 'UNKNOWN';
}

function resolveKindLabel(value?: number): string {
  return KIND_OPTIONS.find((option) => option.value === (value ?? 0))?.label ?? 'UNKNOWN';
}

function parseExamples(value?: AdminIntentNode['examples']): string[] {
  if (!value) {
    return [];
  }
  if (Array.isArray(value)) {
    return value.map((item) => String(item).trim()).filter(Boolean);
  }

  try {
    const parsed = JSON.parse(value);
    if (Array.isArray(parsed)) {
      return parsed.map((item) => String(item).trim()).filter(Boolean);
    }
  } catch {
    // 后端历史数据可能是换行纯文本；解析失败时降级为逐行展示，避免丢失维护信息。
  }

  return value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);
}

function stringifyExamples(text: string): string | undefined {
  const examples = text
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);
  return examples.length > 0 ? JSON.stringify(examples) : undefined;
}

function findNodeByCode(nodes: AdminIntentNode[], code?: string | null): AdminIntentNode | null {
  if (!code) {
    return null;
  }
  for (const node of nodes) {
    if (node.intentCode === code) {
      return node;
    }
    const found = findNodeByCode(node.children ?? [], code);
    if (found) {
      return found;
    }
  }
  return null;
}

function buildTreeOptions(nodes: AdminIntentNode[], prefix = '', result: TreeOption[] = []): TreeOption[] {
  nodes.forEach((node) => {
    const label = prefix ? `${prefix} / ${node.name}` : node.name;
    result.push({ label, value: node.intentCode });
    buildTreeOptions(node.children ?? [], label, result);
  });
  return result;
}

function findPathByCode(
  nodes: AdminIntentNode[],
  code?: string | null,
  trail: AdminIntentNode[] = [],
): AdminIntentNode[] {
  if (!code) {
    return [];
  }

  for (const node of nodes) {
    const nextTrail = [...trail, node];
    if (node.intentCode === code) {
      return nextTrail;
    }
    const found = findPathByCode(node.children ?? [], code, nextTrail);
    if (found.length > 0) {
      return found;
    }
  }
  return [];
}

function buildCascadeColumns(nodes: AdminIntentNode[], selectedCode?: string | null): AdminIntentNode[][] {
  if (nodes.length === 0) {
    return [];
  }

  const columns: AdminIntentNode[][] = [nodes];
  const path = findPathByCode(nodes, selectedCode);
  let currentLevelNodes = nodes;

  // 分栏级联按“当前选中路径”逐列展开子节点，保证点击任意祖先节点时列内容立即对齐。
  for (const node of path) {
    const matched = currentLevelNodes.find((candidate) => candidate.intentCode === node.intentCode);
    if (!matched) {
      break;
    }
    const children = matched.children ?? [];
    if (children.length === 0) {
      break;
    }
    columns.push(children);
    currentLevelNodes = children;
  }

  return columns;
}

function defaultFormFromNode(node: AdminIntentNode): IntentFormState {
  const kind = resolveKind(node);
  return {
    intentCode: node.intentCode,
    name: node.name,
    parentCode: node.parentCode || ROOT_PARENT,
    level: String(node.level ?? 0),
    kind: String(kind),
    searchScopeId: node.kbId ?? '',
    collectionName: node.collectionName ?? '',
    topK: node.topK ? String(node.topK) : '',
    sortOrder: String(node.sortOrder ?? node.sortNo ?? 0),
    enabled: node.enabled !== 0,
    description: node.description ?? '',
    examplesText: parseExamples(node.examples).join('\n'),
    promptSnippet: node.promptSnippet ?? '',
    promptTemplate: node.promptTemplate ?? '',
    mcpToolId: node.mcpToolId ?? '',
    paramPromptTemplate: node.paramPromptTemplate ?? '',
  };
}

function defaultFormForCreate(parentNode: AdminIntentNode | null): IntentFormState {
  const parentKind = parentNode ? resolveKind(parentNode) : 0;
  const nextLevel = parentNode ? Math.min((parentNode.level ?? 0) + 1, 2) : 0;
  return {
    ...emptyForm,
    parentCode: parentNode?.intentCode ?? ROOT_PARENT,
    level: String(nextLevel),
    kind: String(parentKind),
  };
}

function toOptionalNumber(value: string): number | undefined {
  if (value.trim() === '') {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function buildPayload(form: IntentFormState, editingNode?: AdminIntentNode | null): AdminIntentNode {
  const kind = Number(form.kind);
  const sortOrder = toOptionalNumber(form.sortOrder) ?? 0;
  // 编辑节点回到根节点时显式传空串，后端会归一化为 null，避免字段缺失被误判为“不修改父节点”。
  const parentCode = form.parentCode === ROOT_PARENT ? '' : form.parentCode;

  return {
    id: editingNode?.id,
    intentCode: form.intentCode.trim(),
    name: form.name.trim(),
    parentCode,
    description: form.description.trim() || undefined,
    intentType: resolveIntentType(kind),
    // 仍复用 kbId 字段承载“搜索域标识”，确保历史表结构兼容。
    kbId: kind === 0 ? form.searchScopeId.trim() || undefined : undefined,
    collectionName: kind === 0 ? form.collectionName.trim() || undefined : undefined,
    topK: toOptionalNumber(form.topK),
    kind,
    level: toOptionalNumber(form.level) ?? 0,
    examples: stringifyExamples(form.examplesText),
    promptSnippet: form.promptSnippet.trim() || undefined,
    promptTemplate: form.promptTemplate.trim() || undefined,
    mcpToolId: kind === 2 ? form.mcpToolId.trim() || undefined : undefined,
    paramPromptTemplate: kind === 2 ? form.paramPromptTemplate.trim() || undefined : undefined,
    enabled: form.enabled ? 1 : 0,
    sortNo: sortOrder,
    sortOrder,
  };
}

function extractErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error ? error.message || fallback : fallback;
}

export function IntentTreePage() {
  const adminMessage = useAdminMessage();
  const [tree, setTree] = React.useState<AdminIntentNode[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [errorMessage, setErrorMessage] = React.useState('');
  const [selectedCode, setSelectedCode] = React.useState<string | null>(null);
  const [treeViewMode, setTreeViewMode] = React.useState<IntentTreeViewMode>('tree');
  const [expandedMap, setExpandedMap] = React.useState<Record<string, boolean>>({});
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [dialogMode, setDialogMode] = React.useState<DialogMode>('create');
  const [dialogParent, setDialogParent] = React.useState<AdminIntentNode | null>(null);
  const [editingNode, setEditingNode] = React.useState<AdminIntentNode | null>(null);
  const [deleteTarget, setDeleteTarget] = React.useState<AdminIntentNode | null>(null);

  const selectedNode = React.useMemo(() => findNodeByCode(tree, selectedCode), [tree, selectedCode]);
  const selectedPath = React.useMemo(() => findPathByCode(tree, selectedCode), [tree, selectedCode]);
  const selectedPathCodes = React.useMemo(
    () => new Set(selectedPath.map((node) => node.intentCode)),
    [selectedPath],
  );
  const cascadeColumns = React.useMemo(() => buildCascadeColumns(tree, selectedCode), [tree, selectedCode]);
  const treeOptions = React.useMemo(() => buildTreeOptions(tree), [tree]);

  const reload = React.useCallback(async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      const nextTree = await AdminChatApi.listIntentTree();
      setTree(nextTree ?? []);
      setSelectedCode((previousCode) => {
        if (previousCode && findNodeByCode(nextTree ?? [], previousCode)) {
          return previousCode;
        }
        return nextTree?.[0]?.intentCode ?? null;
      });
    } catch (error) {
      setErrorMessage(extractErrorMessage(error, '加载意图树失败'));
    } finally {
      setLoading(false);
    }
  }, []);

  React.useEffect(() => {
    void reload();
  }, [reload]);

  const openCreateDialog = (parentNode: AdminIntentNode | null) => {
    setDialogMode('create');
    setDialogParent(parentNode);
    setEditingNode(null);
    setDialogOpen(true);
  };

  const openEditDialog = (node: AdminIntentNode) => {
    setDialogMode('edit');
    setDialogParent(null);
    setEditingNode(node);
    setDialogOpen(true);
  };

  const handleDelete = async () => {
    if (!deleteTarget?.id) {
      return;
    }
    try {
      await AdminChatApi.deleteIntent(deleteTarget.id);
      setDeleteTarget(null);
      await reload();
      void adminMessage.success('意图节点已删除');
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, '删除节点失败'));
    }
  };

  return (
    <div
      data-testid="intent-tree-page-shell"
      className="w-full p-lg space-y-lg xl:flex xl:h-full xl:min-h-0 xl:flex-col xl:overflow-hidden"
    >
      <div className="flex flex-col gap-md lg:flex-row lg:items-start lg:justify-between">
        <div>
          <Typography.Title level={2} style={{ margin: 0 }}>意图树配置</Typography.Title>
          <Typography.Paragraph className="mt-1 mb-0" type="secondary">
            维护管理端意图层级、检索参数与 MCP 工具绑定，运行时仍兼容 intentType。
          </Typography.Paragraph>
        </div>
        <Space wrap>
          <Button aria-label="刷新" icon={<ReloadOutlined />} onClick={() => void reload()}>
            刷新
          </Button>
          <Button aria-label="新建根节点" icon={<PlusOutlined />} type="primary" onClick={() => openCreateDialog(null)}>
            新建根节点
          </Button>
        </Space>
      </div>

      {errorMessage ? (
        <Alert showIcon type="error" message={errorMessage} />
      ) : null}

      <div className="grid grid-cols-1 gap-lg xl:flex-1 xl:min-h-0 xl:grid-cols-[minmax(320px,0.95fr)_minmax(0,1.25fr)] xl:overflow-hidden">
        <section
          aria-label="意图树结构"
          className="rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-sm xl:flex xl:min-h-0 xl:flex-col xl:overflow-hidden"
        >
          <div className="border-b border-border-hairline px-lg py-md">
            <div className="flex flex-col gap-sm lg:flex-row lg:items-start lg:justify-between">
              <div>
                <Typography.Title level={4} style={{ margin: 0 }}>意图树结构</Typography.Title>
                <Typography.Paragraph className="mt-1 mb-0" type="secondary">
                  点击节点查看详情，徽标展示层级与运行类型。
                </Typography.Paragraph>
              </div>
              <Space.Compact>
                <Button
                  aria-pressed={treeViewMode === 'tree'}
                  type={treeViewMode === 'tree' ? 'primary' : 'default'}
                  onClick={() => setTreeViewMode('tree')}
                >
                  树形结构
                </Button>
                <Button
                  aria-pressed={treeViewMode === 'cascade'}
                  type={treeViewMode === 'cascade' ? 'primary' : 'default'}
                  onClick={() => setTreeViewMode('cascade')}
                >
                  分栏级联
                </Button>
              </Space.Compact>
            </div>
          </div>
          <div
            data-testid="intent-tree-list-scroll"
            className={[
              'p-md xl:flex-1 xl:min-h-0 xl:overflow-y-auto',
              'admin-intent-tree-scroll',
              treeViewMode === 'tree' ? 'space-y-xs' : '',
            ].join(' ')}
          >
            {loading ? (
              <div className="rounded-xl bg-surface-container-low px-lg py-xl text-center">
                <Spin />
                <Typography.Paragraph className="mt-sm mb-0" type="secondary">加载中...</Typography.Paragraph>
              </div>
            ) : tree.length === 0 ? (
              <Empty className="rounded-xl bg-surface-container-low px-lg py-xl" description="暂无节点，请先创建根节点。" />
            ) : (
              treeViewMode === 'tree' ? (
                <IntentAntdTree
                  nodes={tree}
                  selectedCode={selectedCode}
                  expandedMap={expandedMap}
                  onToggle={(intentCode) =>
                    setExpandedMap((previous) => ({
                      ...previous,
                      [intentCode]: !(previous[intentCode] ?? true),
                    }))
                  }
                  onExpand={(nextExpandedMap) => setExpandedMap(nextExpandedMap)}
                  onSelect={setSelectedCode}
                />
              ) : (
                <IntentCascadeColumns
                  columns={cascadeColumns}
                  selectedCode={selectedCode}
                  selectedPathCodes={selectedPathCodes}
                  onSelect={setSelectedCode}
                />
              )
            )}
          </div>
        </section>

        <section
          aria-label="节点详情"
          className="rounded-2xl border border-border-hairline bg-surface-container-lowest shadow-sm xl:flex xl:min-h-0 xl:flex-col xl:overflow-hidden"
        >
          <div className="border-b border-border-hairline px-lg py-md">
            <h3 className="font-title-md text-title-md text-ink">节点详情</h3>
            <p className="mt-1 text-body-sm text-secondary">查看当前选中节点并进行新增、编辑或删除。</p>
          </div>
          {/* 步骤：桌面端将详情区限制在视口内，避免左侧树变长时把整个管理页一起撑出滚动。 */}
          <div
            data-testid="intent-tree-detail-scroll"
            className="xl:flex-1 xl:min-h-0 xl:overflow-y-auto"
          >
            <IntentNodeDetail
              node={selectedNode}
              onCreateChild={() => selectedNode && openCreateDialog(selectedNode)}
              onEdit={() => selectedNode && openEditDialog(selectedNode)}
              onDelete={() => selectedNode && setDeleteTarget(selectedNode)}
            />
          </div>
        </section>
      </div>

      {dialogOpen ? (
        <IntentNodeDialog
          mode={dialogMode}
          parentNode={dialogParent}
          editingNode={editingNode}
          treeOptions={treeOptions}
          onClose={() => setDialogOpen(false)}
          onSubmit={async (payload) => {
            if (dialogMode === 'edit' && editingNode?.id) {
              await AdminChatApi.updateIntent(editingNode.id, payload);
            } else {
              await AdminChatApi.createIntent(payload);
            }
            setDialogOpen(false);
            await reload();
            void adminMessage.success(dialogMode === 'edit' ? '意图节点已保存' : '意图节点已创建');
          }}
        />
      ) : null}

      {deleteTarget ? (
        <DeleteConfirmDialog
          node={deleteTarget}
          onCancel={() => setDeleteTarget(null)}
          onConfirm={() => void handleDelete()}
        />
      ) : null}
    </div>
  );
}

interface IntentTreeDataNode extends DataNode {
  key: string;
  title: string;
  rawNode: AdminIntentNode;
  children?: IntentTreeDataNode[];
}

interface IntentAntdTreeProps {
  nodes: AdminIntentNode[];
  selectedCode: string | null;
  expandedMap: Record<string, boolean>;
  onToggle: (intentCode: string) => void;
  onExpand: (expandedMap: Record<string, boolean>) => void;
  onSelect: (intentCode: string) => void;
}

/**
 * AntD Tree 承载意图层级；节点标题保留业务徽标和显式选择/展开按钮，兼容键盘与测试可访问名称。
 */
function IntentAntdTree({ nodes, selectedCode, expandedMap, onToggle, onExpand, onSelect }: IntentAntdTreeProps) {
  const treeData = React.useMemo(() => buildIntentTreeData(nodes, expandedMap), [expandedMap, nodes]);
  const expandableKeys = React.useMemo(() => collectExpandableIntentKeys(nodes), [nodes]);
  const expandedKeys = React.useMemo(
    () => expandableKeys.filter((key) => expandedMap[key] ?? true),
    [expandableKeys, expandedMap],
  );

  return (
    <Tree<IntentTreeDataNode>
      blockNode
      className="admin-intent-tree"
      expandedKeys={expandedKeys}
      key={expandedKeys.join('|') || 'intent-tree-collapsed'}
      selectedKeys={selectedCode ? [selectedCode] : []}
      switcherIcon={<DownOutlined />}
      treeData={treeData}
      titleRender={(treeNode) => {
        const node = treeNode.rawNode;
        const children = node.children ?? [];
        const hasChildren = children.length > 0;
        const isExpanded = expandedMap[node.intentCode] ?? true;
        const isSelected = selectedCode === node.intentCode;
        return (
          <div
            data-testid={`intent-tree-node-${node.intentCode}`}
            className={[
              'admin-intent-tree-row',
              hasChildren ? 'admin-intent-tree-row-branch' : 'admin-intent-tree-row-leaf',
              isSelected ? 'admin-intent-tree-row-selected' : '',
            ].join(' ')}
          >
            {hasChildren ? (
              <Button
                aria-label={`${isExpanded ? '收起' : '展开'} ${node.name}`}
                className="admin-intent-tree-toggle"
                icon={<DownOutlined rotate={isExpanded ? 0 : -90} />}
                size="small"
                type="text"
                onClick={(event) => {
                  event.stopPropagation();
                  onToggle(node.intentCode);
                }}
              />
            ) : (
              <span className="admin-intent-tree-toggle-placeholder" />
            )}
            <Button
              aria-label={`选择节点 ${node.name}`}
              className="admin-intent-tree-select"
              type="text"
              onClick={(event) => {
                event.stopPropagation();
                onSelect(node.intentCode);
              }}
            >
              <span className="admin-intent-tree-label">
                <span className="admin-intent-tree-name">{node.name}</span>
                <span className="admin-intent-tree-code">{node.intentCode}</span>
              </span>
            </Button>
            <div className="admin-intent-tree-badges">
              <IntentBadge>{resolveLevelLabel(node.level)}</IntentBadge>
              <IntentBadge tone={resolveKind(node) === 2 ? 'strong' : 'soft'}>{resolveKindLabel(resolveKind(node))}</IntentBadge>
            </div>
          </div>
        );
      }}
      onExpand={(keys) => {
        const expandedSet = new Set(keys.map(String));
        const nextMap: Record<string, boolean> = {};
        expandableKeys.forEach((key) => {
          nextMap[key] = expandedSet.has(key);
        });
        onExpand(nextMap);
      }}
      onSelect={(_, info) => {
        onSelect(info.node.rawNode.intentCode);
      }}
    />
  );
}

function buildIntentTreeData(nodes: AdminIntentNode[], expandedMap: Record<string, boolean>): IntentTreeDataNode[] {
  return nodes.map((node) => ({
    key: node.intentCode,
    title: node.name,
    rawNode: node,
    // AntD Tree 的折叠动画会短暂保留子节点 DOM；按展开状态裁剪 children，保证折叠后测试与读屏结构都立即收敛。
    children: expandedMap[node.intentCode] ?? true ? buildIntentTreeData(node.children ?? [], expandedMap) : undefined,
  }));
}

function collectExpandableIntentKeys(nodes: AdminIntentNode[]): string[] {
  return nodes.flatMap((node) => {
    const children = node.children ?? [];
    if (children.length === 0) {
      return [];
    }
    return [node.intentCode, ...collectExpandableIntentKeys(children)];
  });
}

interface IntentCascadeColumnsProps {
  columns: AdminIntentNode[][];
  selectedCode: string | null;
  selectedPathCodes: Set<string>;
  onSelect: (intentCode: string) => void;
}

function IntentCascadeColumns({
  columns,
  selectedCode,
  selectedPathCodes,
  onSelect,
}: IntentCascadeColumnsProps) {
  return (
    <div
      data-testid="intent-cascade-columns"
      className="admin-intent-cascade admin-intent-tree-scroll flex items-start gap-sm overflow-x-auto pb-xs"
    >
      {columns.map((column, columnIndex) => (
        <div
          key={`cascade-column-${columnIndex}`}
          data-testid={`intent-cascade-column-${columnIndex}`}
          className="admin-intent-cascade-column min-w-[200px] flex-1 rounded-xl border border-border-hairline bg-surface-container-low"
        >
          <div className="admin-intent-cascade-column-header border-b border-border-hairline px-sm py-xs">
            <p className="text-[12px] font-medium text-secondary">
              {columnIndex === 0 ? 'ROOT' : `第 ${columnIndex + 1} 级`}
            </p>
          </div>
          <div className="admin-intent-cascade-column-body space-y-xs p-sm">
            {column.map((node) => {
              const isSelected = selectedCode === node.intentCode;
              const inPath = selectedPathCodes.has(node.intentCode);
              return (
                <Button
                  key={node.intentCode}
                  aria-label={`级联选择 ${node.name}`}
                  block
                  className={[
                    'admin-intent-cascade-item',
                    isSelected
                      ? 'admin-intent-cascade-item-selected'
                      : inPath
                        ? 'admin-intent-cascade-item-path'
                        : '',
                  ].join(' ')}
                  type="text"
                  onClick={() => onSelect(node.intentCode)}
                >
                  <div className="admin-intent-cascade-row">
                    <div className="admin-intent-cascade-label">
                      <span className="admin-intent-cascade-name">{node.name}</span>
                      <span className="admin-intent-cascade-code">
                        {node.intentCode}
                      </span>
                    </div>
                    <div className="admin-intent-cascade-badges">
                      <IntentBadge>{resolveLevelLabel(node.level)}</IntentBadge>
                      <IntentBadge tone={resolveKind(node) === 2 ? 'strong' : 'soft'}>
                        {resolveKindLabel(resolveKind(node))}
                      </IntentBadge>
                    </div>
                  </div>
                </Button>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}

interface IntentNodeDetailProps {
  node: AdminIntentNode | null;
  onCreateChild: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

function IntentNodeDetail({ node, onCreateChild, onEdit, onDelete }: IntentNodeDetailProps) {
  if (!node) {
    return (
      <div className="p-xl text-center text-secondary">
        请选择左侧节点，或先新建根节点初始化意图树。
      </div>
    );
  }

  const examples = parseExamples(node.examples);
  const kind = resolveKind(node);

  return (
    <div className="space-y-lg p-lg">
      <div className="flex flex-col gap-md lg:flex-row lg:items-start lg:justify-between">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-xs">
            <h4 className="font-title-md text-title-md text-ink">{node.name}</h4>
            <IntentBadge>{resolveLevelLabel(node.level)}</IntentBadge>
            <IntentBadge tone={kind === 2 ? 'strong' : 'soft'}>{resolveKindLabel(kind)}</IntentBadge>
            <IntentBadge tone={node.enabled === 0 ? 'muted' : 'soft'}>
              {node.enabled === 0 ? '停用' : '启用'}
            </IntentBadge>
          </div>
          <p className="mt-1 font-data-mono text-[12px] text-secondary">{node.intentCode}</p>
        </div>
        <Space wrap size={8}>
          <Button aria-label="新建子节点" icon={<FolderAddOutlined />} type="primary" onClick={onCreateChild}>
            新建子节点
          </Button>
          <Button aria-label="编辑节点" icon={<EditOutlined />} onClick={onEdit}>
            编辑节点
          </Button>
          <Button aria-label="删除节点" danger icon={<DeleteOutlined />} onClick={onDelete}>
            删除节点
          </Button>
        </Space>
      </div>

      <div className="grid gap-sm md:grid-cols-2">
        <DetailItem label="父节点" value={node.parentCode || 'ROOT'} />
        <DetailItem label="排序" value={String(node.sortOrder ?? node.sortNo ?? 0)} />
        <DetailItem label="Collection" value={node.collectionName || '-'} />
        <DetailItem label="节点 TopK" value={node.topK ? String(node.topK) : '默认（全局）'} />
        <DetailItem label="MCP 工具ID" value={node.mcpToolId || '-'} />
        <DetailItem label="运行类型" value={node.intentType || resolveIntentType(kind)} />
      </div>

      <div className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
        <p className="font-title-sm text-ink">描述</p>
        <p className="mt-2 whitespace-pre-wrap text-body-sm text-secondary">
          {node.description || '暂无描述'}
        </p>
      </div>

      <div className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
        <p className="font-title-sm text-ink">示例问题</p>
        <div className="mt-2 flex flex-wrap gap-xs">
          {examples.length > 0 ? (
            examples.map((example) => <IntentBadge key={example}>{example}</IntentBadge>)
          ) : (
            <span className="text-body-sm text-secondary">暂无示例</span>
          )}
        </div>
      </div>
    </div>
  );
}

function DetailItem({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-border-hairline bg-surface-container-low px-md py-sm">
      <p className="text-[12px] text-secondary">{label}</p>
      <p className="mt-1 break-words font-medium text-ink">{value}</p>
    </div>
  );
}

function IntentBadge({ children, tone = 'muted' }: { children: React.ReactNode; tone?: 'muted' | 'soft' | 'strong' }) {
  const toneClass =
    tone === 'strong'
      ? 'border-ink bg-ink text-on-ink'
      : tone === 'soft'
        ? 'border-border-strong bg-surface-container text-ink'
        : 'border-border-hairline bg-surface-container-low text-secondary';

  return (
    <Tag className={`m-0 rounded-full border px-2 py-0.5 text-[11px] font-medium ${toneClass}`}>
      {children}
    </Tag>
  );
}

interface IntentNodeDialogProps {
  mode: DialogMode;
  parentNode: AdminIntentNode | null;
  editingNode: AdminIntentNode | null;
  treeOptions: TreeOption[];
  onClose: () => void;
  onSubmit: (payload: AdminIntentNode) => Promise<void>;
}

function IntentNodeDialog({
  mode,
  parentNode,
  editingNode,
  treeOptions,
  onClose,
  onSubmit,
}: IntentNodeDialogProps) {
  const adminMessage = useAdminMessage();
  const [form, setForm] = React.useState<IntentFormState>(() =>
    mode === 'edit' && editingNode ? defaultFormFromNode(editingNode) : defaultFormForCreate(parentNode),
  );
  const [saving, setSaving] = React.useState(false);
  const [fieldErrors, setFieldErrors] = React.useState<Record<string, string>>({});

  const filteredTreeOptions =
    mode === 'edit' && editingNode
      ? treeOptions.filter((option) => option.value !== editingNode.intentCode)
      : treeOptions;
  const kind = Number(form.kind);

  const updateField = (field: keyof IntentFormState, value: string | boolean) => {
    setFieldErrors((previous) => ({ ...previous, [field]: '' }));
    setForm((previous) => ({ ...previous, [field]: value }));
  };

  const validate = () => {
    const nextErrors: Record<string, string> = {};
    if (!form.name.trim()) {
      nextErrors.name = '请输入节点名称';
    }
    if (!form.intentCode.trim()) {
      nextErrors.intentCode = '请输入意图标识';
    }
    if (kind === 2 && !form.mcpToolId.trim()) {
      nextErrors.mcpToolId = 'MCP 类型必须填写 MCP 工具ID';
    }
    setFieldErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = async () => {
    if (!validate()) {
      return;
    }

    setSaving(true);
    try {
      await onSubmit(buildPayload(form, editingNode));
    } catch (error) {
      void adminMessage.error(extractErrorMessage(error, mode === 'create' ? '创建节点失败' : '保存节点失败'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      centered
      closeIcon={<CloseOutlined aria-hidden="true" />}
      footer={null}
      maskTransitionName=""
      open
      title={mode === 'create' ? '新建意图节点' : '编辑意图节点'}
      transitionName=""
      width={800}
      onCancel={onClose}
    >
      <Form className="space-y-lg" layout="vertical" onFinish={() => void handleSubmit()}>
        <Typography.Paragraph className="mb-0" type="secondary">
          按统一配置台分组维护基础信息、示例、Prompt 与高级参数。
        </Typography.Paragraph>
        <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
          <legend className="px-xs font-title-sm text-ink">基础信息</legend>
          <div className="grid gap-md md:grid-cols-2">
            <TextField
              id="intent-node-name"
              label="节点名称"
              value={form.name}
              error={fieldErrors.name}
              onChange={(value) => updateField('name', value)}
            />
            <TextField
              id="intent-node-code"
              label="意图标识"
              value={form.intentCode}
              error={fieldErrors.intentCode}
              disabled={mode === 'edit'}
              onChange={(value) => updateField('intentCode', value)}
            />
            <SelectField
              id="intent-node-level"
              label="节点层级"
              value={form.level}
              onChange={(value) => updateField('level', value)}
              options={LEVEL_OPTIONS.map((option) => ({
                value: String(option.value),
                label: `${option.label} - ${option.description}`,
              }))}
            />
            <SelectField
              id="intent-node-kind"
              label="节点类型"
              value={form.kind}
              onChange={(value) => updateField('kind', value)}
              options={KIND_OPTIONS.map((option) => ({
                value: String(option.value),
                label: `${option.label} - ${option.description}`,
              }))}
            />
            <SelectField
              id="intent-node-parent"
              label="父节点"
              value={form.parentCode}
              onChange={(value) => updateField('parentCode', value)}
              options={[
                { value: ROOT_PARENT, label: 'ROOT' },
                ...filteredTreeOptions.map((option) => ({ value: option.value, label: option.label })),
              ]}
            />
            {kind === 0 ? (
              <>
                <TextField
                  id="intent-node-search-scope"
                  label="搜索域标识"
                  value={form.searchScopeId}
                  onChange={(value) => updateField('searchScopeId', value)}
                />
                <TextField
                  id="intent-node-collection"
                  label="Collection"
                  value={form.collectionName}
                  onChange={(value) => updateField('collectionName', value)}
                />
              </>
            ) : null}
            {kind === 2 ? (
              <TextField
                id="intent-node-mcp"
                label="MCP 工具ID"
                value={form.mcpToolId}
                error={fieldErrors.mcpToolId}
                onChange={(value) => updateField('mcpToolId', value)}
              />
            ) : null}
          </div>
        </fieldset>

        <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
          <legend className="px-xs font-title-sm text-ink">描述与示例</legend>
          <div className="space-y-md">
            <TextAreaField
              id="intent-node-description"
              label="描述"
              value={form.description}
              rows={3}
              onChange={(value) => updateField('description', value)}
            />
            <TextAreaField
              id="intent-node-examples"
              label="示例问题"
              value={form.examplesText}
              rows={4}
              onChange={(value) => updateField('examplesText', value)}
            />
          </div>
        </fieldset>

        <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
          <legend className="px-xs font-title-sm text-ink">Prompt 配置</legend>
          <div className="space-y-md">
            <TextAreaField
              id="intent-node-prompt-snippet"
              label="Prompt 片段"
              value={form.promptSnippet}
              rows={3}
              onChange={(value) => updateField('promptSnippet', value)}
            />
            <TextAreaField
              id="intent-node-prompt-template"
              label="Prompt 模板"
              value={form.promptTemplate}
              rows={4}
              onChange={(value) => updateField('promptTemplate', value)}
            />
            {kind === 2 ? (
              <TextAreaField
                id="intent-node-param-prompt"
                label="参数提取提示词"
                value={form.paramPromptTemplate}
                rows={3}
                onChange={(value) => updateField('paramPromptTemplate', value)}
              />
            ) : null}
          </div>
        </fieldset>

        <fieldset className="rounded-xl border border-border-hairline bg-surface-container-low p-md">
          <legend className="px-xs font-title-sm text-ink">高级设置</legend>
          <div className="grid gap-md md:grid-cols-3">
            <TextField
              id="intent-node-topk"
              label="节点 TopK"
              type="number"
              value={form.topK}
              onChange={(value) => updateField('topK', value)}
            />
            <TextField
              id="intent-node-sort"
              label="排序"
              type="number"
              value={form.sortOrder}
              onChange={(value) => updateField('sortOrder', value)}
            />
            <Form.Item className="mb-0" label="启用状态">
              <Switch
                checked={form.enabled}
                checkedChildren="启用"
                unCheckedChildren="停用"
                onChange={(checked) => updateField('enabled', checked)}
              />
            </Form.Item>
          </div>
        </fieldset>

        <div className="flex flex-wrap justify-end gap-sm">
          <Button onClick={onClose} disabled={saving}>
            取消
          </Button>
          <Button
            aria-label={mode === 'create' ? '创建节点' : '保存节点'}
            htmlType="submit"
            type="primary"
            icon={<SaveOutlined />}
            loading={saving}
          >
            {mode === 'create' ? '创建节点' : '保存节点'}
          </Button>
        </div>
      </Form>
    </Modal>
  );
}

interface BaseFieldProps {
  id: string;
  label: string;
  value: string;
  error?: string;
  disabled?: boolean;
  onChange: (value: string) => void;
}

function TextField({
  id,
  label,
  value,
  error,
  disabled,
  type = 'text',
  onChange,
}: BaseFieldProps & { type?: string }) {
  return (
    <Form.Item
      className="mb-0"
      help={error}
      htmlFor={id}
      label={label}
      validateStatus={error ? 'error' : undefined}
    >
      <Input
        id={id}
        type={type}
        value={value}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
    </Form.Item>
  );
}

function SelectField({
  id,
  label,
  value,
  options,
  onChange,
}: BaseFieldProps & { options: Array<{ value: string; label: string }> }) {
  return (
    <Form.Item className="mb-0" htmlFor={id} label={label}>
      <Select
        id={id}
        value={value}
        options={options}
        popupMatchSelectWidth={false}
        onChange={onChange}
      />
    </Form.Item>
  );
}

function TextAreaField({
  id,
  label,
  value,
  rows,
  onChange,
}: Omit<BaseFieldProps, 'error' | 'disabled'> & { rows: number }) {
  return (
    <Form.Item className="mb-0" htmlFor={id} label={label}>
      <Input.TextArea
        id={id}
        rows={rows}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </Form.Item>
  );
}

function DeleteConfirmDialog({
  node,
  onCancel,
  onConfirm,
}: {
  node: AdminIntentNode;
  onCancel: () => void;
  onConfirm: () => void;
}) {
  return (
    <Modal
      centered
      closeIcon={<CloseOutlined aria-hidden="true" />}
      cancelText="取消"
      data-testid="intent-delete-dialog-overlay"
      maskTransitionName=""
      okButtonProps={{ danger: true, icon: <DeleteOutlined />, 'aria-label': '确认删除' }}
      okText="确认删除"
      open
      title="删除意图节点"
      transitionName=""
      onCancel={onCancel}
      onOk={onConfirm}
    >
      <Typography.Paragraph className="mb-0" type="secondary">
        将删除节点「{node.name}」。如后端检测到子节点或运行时引用，会返回中文错误提示。
      </Typography.Paragraph>
    </Modal>
  );
}
