import React from 'react';
import { AdminChatApi, AdminQueryTermMapping } from '../api/adminChatApi';

export function QueryTermMappingPage() {
  const [mappings, setMappings] = React.useState<AdminQueryTermMapping[]>([]);
  const [draft, setDraft] = React.useState<AdminQueryTermMapping>({ sourceTerm: '', targetTerm: '', mappingType: 'alias' });

  const reload = React.useCallback(async () => {
    setMappings(await AdminChatApi.listMappings());
  }, []);

  React.useEffect(() => {
    void reload();
  }, [reload]);

  return (
    <div className="p-lg w-full space-y-lg">
      <div>
        <h2 className="font-headline-md text-headline-md text-ink">关键词映射</h2>
        <p className="text-secondary mt-1">维护查询词归一化规则。</p>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-[360px_minmax(0,1fr)] gap-lg">
        <form
          className="bg-surface-container-lowest border border-border-hairline rounded-xl p-lg space-y-md"
          onSubmit={async (event) => {
            event.preventDefault();
            await AdminChatApi.saveMapping(draft);
            setDraft({ sourceTerm: '', targetTerm: '', mappingType: 'alias' });
            await reload();
          }}
        >
          <input value={draft.sourceTerm} onChange={(e) => setDraft({ ...draft, sourceTerm: e.target.value })} placeholder="源词" className="w-full rounded-lg border border-border-hairline px-3 py-2" />
          <input value={draft.targetTerm} onChange={(e) => setDraft({ ...draft, targetTerm: e.target.value })} placeholder="目标词" className="w-full rounded-lg border border-border-hairline px-3 py-2" />
          <input value={draft.mappingType} onChange={(e) => setDraft({ ...draft, mappingType: e.target.value })} placeholder="映射类型" className="w-full rounded-lg border border-border-hairline px-3 py-2" />
          <button className="px-lg py-2 rounded-lg bg-ink text-on-ink">保存映射</button>
        </form>
        <div className="bg-surface-container-lowest border border-border-hairline rounded-xl overflow-hidden">
          <table className="w-full text-left">
            <thead className="bg-surface-container-low">
              <tr>
                <th className="px-lg py-md">源词</th>
                <th className="px-lg py-md">目标词</th>
                <th className="px-lg py-md">类型</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border-hairline">
              {mappings.map((mapping) => (
                <tr key={`${mapping.sourceTerm}-${mapping.targetTerm}-${mapping.id ?? 'new'}`}>
                  <td className="px-lg py-md">{mapping.sourceTerm}</td>
                  <td className="px-lg py-md">{mapping.targetTerm}</td>
                  <td className="px-lg py-md">{mapping.mappingType}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
