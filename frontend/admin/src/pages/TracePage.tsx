import React from 'react';
import { AdminChatApi, AdminTraceDetail, AdminTraceRun } from '../api/adminChatApi';

export function TracePage() {
  const [traces, setTraces] = React.useState<AdminTraceRun[]>([]);
  const [selectedTrace, setSelectedTrace] = React.useState<AdminTraceDetail | null>(null);

  React.useEffect(() => {
    void AdminChatApi.listTraces().then(setTraces);
  }, []);

  return (
    <div className="p-lg w-full space-y-lg">
      <div>
        <h2 className="font-headline-md text-headline-md text-ink">Trace 管理</h2>
        <p className="text-secondary mt-1">查看聊天链路根记录与节点详情。</p>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-[360px_minmax(0,1fr)] gap-lg">
        <div className="bg-surface-container-lowest border border-border-hairline rounded-xl overflow-hidden">
          <div className="px-lg py-md border-b border-border-hairline font-title-md text-ink">最近链路</div>
          <div className="divide-y divide-border-hairline">
            {traces.map((trace) => (
              <button
                key={trace.traceId}
                type="button"
                onClick={async () => setSelectedTrace(await AdminChatApi.getTrace(trace.traceId))}
                className="w-full px-lg py-md text-left hover:bg-surface-container-low transition-colors"
              >
                <div className="font-medium text-ink">{trace.traceName}</div>
                <div className="mt-1 text-[12px] text-secondary font-data-mono">{trace.traceId}</div>
                <div className="mt-2 text-[12px] text-secondary">{trace.status}</div>
              </button>
            ))}
          </div>
        </div>
        <div className="bg-surface-container-lowest border border-border-hairline rounded-xl p-lg">
          <div className="font-title-md text-ink mb-md">链路详情</div>
          {selectedTrace ? (
            <div className="space-y-md">
              <div className="rounded-lg bg-surface-container-low p-md">
                <div className="text-body-sm text-secondary">TraceId</div>
                <div className="font-data-mono text-ink text-[12px] mt-1">{selectedTrace.traceRun.traceId}</div>
              </div>
              <div className="space-y-sm">
                {selectedTrace.nodes.map((node) => (
                  <div key={node.id} className="rounded-lg border border-border-hairline p-md">
                    <div className="font-medium text-ink">{node.nodeName}</div>
                    <div className="text-[12px] text-secondary mt-1">{node.nodeType} / {node.status}</div>
                    {node.className ? <div className="font-data-mono text-[11px] text-tertiary-container mt-2">{node.className}#{node.methodName}</div> : null}
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="text-secondary">请选择左侧链路查看详情</div>
          )}
        </div>
      </div>
    </div>
  );
}
