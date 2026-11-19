import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { ConfirmDialog } from '../../shared/components/Dialog';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { IncidentStatus, ManagedIncident } from '../../shared/types/api';
import { formatDateTime, INCIDENT_STATUS_LABELS, INCIDENT_TYPE_LABELS } from '../../shared/utils/format';

type Action = { kind: 'resolve' | 'reject'; incident: ManagedIncident };

const FILTERS: Array<IncidentStatus | undefined> = [undefined, 'DA_XU_LY', 'TU_CHOI'];
const TONE: Record<IncidentStatus, 'blue' | 'amber' | 'green' | 'red'> = { MOI: 'blue', DANG_XU_LY: 'amber', DA_XU_LY: 'green', TU_CHOI: 'red' };

/** Đợt 4b — quản lý xử lý sự cố do bảo vệ báo (Đợt 2): nhận xử lý → đã xử lý / từ chối (báo nhầm). */
export function IncidentsQueueTab() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<IncidentStatus | undefined>();
  const [action, setAction] = useState<Action | null>(null);
  const [note, setNote] = useState('');
  const list = useQuery({ queryKey: queryKeys.managerIncidents(status), queryFn: () => api.managerIncidents(status), staleTime: 0 });

  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.managerIncidentsAll });
  const start = useMutation({ mutationFn: (id: number) => api.startIncident(id), onSuccess: refresh });
  const act = useMutation({
    mutationFn: ({ kind, incident }: Action) =>
      kind === 'resolve' ? api.resolveIncident(incident.id, note) : api.rejectIncident(incident.id, note),
    onSuccess: async () => {
      await refresh();
      setAction(null);
    },
  });

  return (
    <Card title="Sự cố bảo vệ báo">
      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          {FILTERS.map((filter) => (
            <button
              key={filter ?? 'OPEN'}
              type="button"
              onClick={() => setStatus(filter)}
              className={`rounded-full px-3 py-1 text-sm font-semibold ring-1 ring-inset ${
                status === filter ? 'bg-blue-700 text-white ring-blue-700' : 'bg-white text-slate-600 ring-slate-300'
              }`}
            >
              {filter ? INCIDENT_STATUS_LABELS[filter] : 'Cần xử lý'}
            </button>
          ))}
        </div>
        {start.isError && <Alert tone="error">{errorMessage(start.error)}</Alert>}
        {list.isPending && <LoadingBlock />}
        {list.isError && <QueryError error={list.error} onRetry={() => void list.refetch()} />}
        {list.data?.length === 0 && <p className="py-6 text-center text-slate-500">Không có sự cố nào.</p>}
        <ul className="divide-y divide-slate-100">
          {list.data?.map((incident) => (
            <li key={incident.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
              <div className="min-w-0 flex-1 space-y-1 text-sm">
                <p className="font-semibold">
                  #{incident.id} · {INCIDENT_TYPE_LABELS[incident.type]}
                  {incident.plateDisplay && <span className="ml-2 font-mono">{incident.plateDisplay}</span>}
                </p>
                <p className="text-slate-700">{incident.description}</p>
                <p className="text-xs text-slate-500">{incident.reporterName} báo lúc {formatDateTime(incident.createdAt)}</p>
                {incident.hasPhoto && (
                  <a href={api.managerIncidentPhotoUrl(incident.id)} target="_blank" rel="noreferrer" className="inline-block">
                    <img src={api.managerIncidentPhotoUrl(incident.id)} alt={`Ảnh sự cố #${incident.id}`} className="max-h-32 rounded-lg ring-1 ring-slate-200" />
                  </a>
                )}
                {incident.resolution && (
                  <p className={incident.status === 'TU_CHOI' ? 'text-red-700' : 'text-emerald-800'}>
                    {incident.status === 'TU_CHOI' ? 'Lý do' : 'Kết quả'}: {incident.resolution}
                    {incident.handlerName && <span className="text-xs text-slate-500"> — {incident.handlerName}</span>}
                  </p>
                )}
              </div>
              <div className="flex flex-wrap items-center justify-end gap-2">
                <Badge tone={TONE[incident.status]}>{INCIDENT_STATUS_LABELS[incident.status]}</Badge>
                {incident.status === 'MOI' && (
                  <Button size="sm" loading={start.isPending && start.variables === incident.id} onClick={() => start.mutate(incident.id)}>
                    Nhận xử lý
                  </Button>
                )}
                {incident.status === 'DANG_XU_LY' && (
                  <Button size="sm" variant="success" onClick={() => { act.reset(); setNote(''); setAction({ kind: 'resolve', incident }); }}>
                    Đã xử lý
                  </Button>
                )}
                {(incident.status === 'MOI' || incident.status === 'DANG_XU_LY') && (
                  <Button size="sm" variant="secondary" onClick={() => { act.reset(); setNote(''); setAction({ kind: 'reject', incident }); }}>
                    Từ chối
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>
      {action && (
        <ConfirmDialog
          title={`${action.kind === 'resolve' ? 'Kết quả xử lý' : 'Từ chối'} sự cố #${action.incident.id}`}
          confirmLabel={action.kind === 'resolve' ? 'Đã xử lý' : 'Từ chối'}
          tone={action.kind === 'resolve' ? 'success' : 'danger'}
          pending={act.isPending}
          onCancel={() => setAction(null)}
          onConfirm={() => (note.trim() ? act.mutate(action) : undefined)}
          error={act.isError && <Alert tone="error">{errorMessage(act.error)}</Alert>}
        >
          <label className="block space-y-1 text-sm font-medium text-slate-700">
            {action.kind === 'resolve' ? 'Đã làm gì (bắt buộc)' : 'Lý do (bắt buộc)'}
            <textarea rows={3} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} className="block w-full rounded-lg border border-slate-300 px-3 py-2" />
          </label>
        </ConfirmDialog>
      )}
    </Card>
  );
}
