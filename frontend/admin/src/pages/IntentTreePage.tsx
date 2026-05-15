import React from 'react';
import { AdminChatApi, AdminIntentNode } from '../api/adminChatApi';

export function IntentTreePage() {
  const [nodes, setNodes] = React.useState<AdminIntentNode[]>([]);
  const [draft, setDraft] = React.useState<AdminIntentNode>({ intentCode: '', name: '', intentType: 'kb' });

  const reload = React.useCallback(async () => {
    setNodes(await AdminChatApi.listIntents());
  }, []);

  React.useEffect(() => {
    void reload();
  }, [reload]);

  return (
    <div className="p-lg w-full space-y-lg">
      <div>
        <h2 className="font-headline-md text-headline-md text-ink">意图树管理</h2>
        <p className="text-secondary mt-1">维护聊天意图节点、类型与 MCP 绑定。</p>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-[420px_minmax(0,1fr)] gap-lg">
        <form
          className="bg-surface-container-lowest border border-border-hairline rounded-xl p-lg space-y-md"
          onSubmit={async (event) => {
            event.preventDefault();
            await AdminChatApi.saveIntent(draft);
            setDraft({ intentCode: '', name: '', intentType: 'kb' });
            await reload();
          }}
        >
          <input value={draft.intentCode} onChange={(e) => setDraft({ ...draft, intentCode: e.target.value })} placeholder="intentCode" className="w-full rounded-lg border border-border-hairline px-3 py-2" />
          <input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="名称" className="w-full rounded-lg border border-border-hairline px-3 py-2" />
          <input value={draft.parentCode ?? ''} onChange={(e) => setDraft({ ...draft, parentCode: e.target.value || undefined })} placeholder="parentCode" className="w-full rounded-lg border border-border-hairline px-3 py-2" />
          <select value={draft.intentType} onChange={(e) => setDraft({ ...draft, intentType: e.target.value })} className="w-full rounded-lg border border-border-hairline px-3 py-2">
            <option value="kb">kb</option>
            <option value="system">system</option>
            <option value="mcp">mcp</option>
          </select>
          <input value={draft.mcpToolId ?? ''} onChange={(e) => setDraft({ ...draft, mcpToolId: e.target.value || undefined })} placeholder="mcpToolId" className="w-full rounded-lg border border-border-hairline px-3 py-2" />
          <button className="px-lg py-2 rounded-lg bg-ink text-on-ink">保存节点</button>
        </form>
        <div className="bg-surface-container-lowest border border-border-hairline rounded-xl overflow-hidden">
          <table className="w-full text-left">
            <thead className="bg-surface-container-low">
              <tr>
                <th className="px-lg py-md">编码</th>
                <th className="px-lg py-md">名称</th>
                <th className="px-lg py-md">类型</th>
                <th className="px-lg py-md">MCP</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {nodes.map((node) => (
                <tr key={`${node.intentCode}-${node.id}`}>
                  <td className="px-lg py-md font-data-mono text-[12px]">{node.intentCode}</td>
                  <td className="px-lg py-md">{node.name}</td>
                  <td className="px-lg py-md">{node.intentType}</td>
                  <td className="px-lg py-md">{node.mcpToolId ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
