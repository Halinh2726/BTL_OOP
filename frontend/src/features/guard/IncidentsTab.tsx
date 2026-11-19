import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { IncidentStatus } from '../../shared/types/api';
import { INCIDENT_STATUS_LABELS, INCIDENT_TYPE_LABELS, formatDateTime } from '../../shared/utils/format';
import { ReportIncidentDialog } from './ReportIncidentDialog';

const STATUS_TONE: Record<IncidentStatus, 'amber' | 'blue' | 'green' | 'gray'> = {
  MOI: 'amber',
  DANG_XU_LY: 'blue',
  DA_XU_LY: 'green',
  TU_CHOI: 'gray',
};

/** Đợt 2 — báo sự cố và theo dõi trạng thái xử lý các sự cố mình đã báo. */
export function IncidentsTab() {
  const [reporting, setReporting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [photoOf, setPhotoOf] = useState<number | null>(null);
  const incidents = useQuery({ queryKey: queryKeys.myIncidents, queryFn: api.myIncidents });

  return (
    <Card
      title="Sự cố tôi đã báo"
      actions={
        <Button variant="danger" onClick={() => setReporting(true)}>
          + Báo sự cố
        </Button>
      }
    >
      {message && (
        <div className="mb-4">
          <Alert tone="success">{message}</Alert>
        </div>
      )}
      {incidents.isPending && <LoadingBlock />}
      {incidents.isError && <QueryError error={incidents.error} onRetry={() => void incidents.refetch()} />}
      {incidents.data?.length === 0 && <p className="py-6 text-center text-slate-500">Bạn chưa báo sự cố nào.</p>}
      <ul className="divide-y divide-slate-100">
        {incidents.data?.map((incident) => (
          <li key={incident.id} className="space-y-1 py-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="font-semibold">
                {INCIDENT_TYPE_LABELS[incident.type]}
                {incident.plateDisplay && <span className="ml-2 font-mono">{incident.plateDisplay}</span>}
              </p>
              <Badge tone={STATUS_TONE[incident.status]}>{INCIDENT_STATUS_LABELS[incident.status]}</Badge>
            </div>
            <p className="text-slate-700">{incident.description}</p>
            {incident.resolution && <p className="text-sm text-emerald-800">Quản lý trả lời: {incident.resolution}</p>}
            <div className="flex items-center gap-3 text-xs text-slate-500">
              <span>{formatDateTime(incident.createdAt)}</span>
              {incident.hasPhoto && (
                <button type="button" className="font-semibold text-blue-700 hover:underline" onClick={() => setPhotoOf(photoOf === incident.id ? null : incident.id)}>
                  {photoOf === incident.id ? 'Ẩn ảnh' : 'Xem ảnh'}
                </button>
              )}
            </div>
            {photoOf === incident.id && (
              <img src={api.incidentPhotoUrl(incident.id)} alt="Ảnh sự cố" className="max-h-64 rounded-lg ring-1 ring-slate-200" />
            )}
          </li>
        ))}
      </ul>
      {reporting && (
        <ReportIncidentDialog
          onClose={() => setReporting(false)}
          onReported={() => {
            setReporting(false);
            setMessage('Đã gửi sự cố cho quản lý. Sự cố chưa xử lý xong sẽ được ghi vào biên bản bàn giao ca.');
          }}
        />
      )}
    </Card>
  );
}
