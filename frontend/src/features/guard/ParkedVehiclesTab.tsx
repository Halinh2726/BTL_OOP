import { useQuery } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import type { ParkedVehicleItem } from '../../shared/types/api';
import { formatDateTime, formatDuration, normalizePlate } from '../../shared/utils/format';
import { ReportIncidentDialog } from './ReportIncidentDialog';

/**
 * Đợt 2 — xe đang trong bãi: tổng số xe, tìm theo biển số, gửi lâu nhất lên đầu; xe gửi quá 24 giờ tô đỏ
 * kèm nút báo sự cố "xe gửi quá lâu". Tự làm mới mỗi 30 giây.
 */
export function ParkedVehiclesTab() {
  const [search, setSearch] = useState('');
  const fragment = useDeferredValue(normalizePlate(search));
  const [reporting, setReporting] = useState<ParkedVehicleItem | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const typeName = useVehicleTypeName();
  const lot = useQuery({
    queryKey: queryKeys.parkedVehicles(fragment),
    queryFn: () => api.parkedVehicles(fragment),
    refetchInterval: 30_000,
    placeholderData: (previous) => previous,
  });

  return (
    <Card>
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-xl bg-blue-50 p-4 text-center">
            <p className="text-sm text-blue-700">Xe đang trong bãi</p>
            <p className="text-4xl font-bold text-blue-900">{lot.data?.total ?? '—'}</p>
          </div>
          <div className={`rounded-xl p-4 text-center ${lot.data?.overdue ? 'bg-red-50' : 'bg-slate-50'}`}>
            <p className={`text-sm ${lot.data?.overdue ? 'text-red-700' : 'text-slate-500'}`}>Gửi quá 24 giờ</p>
            <p className={`text-4xl font-bold ${lot.data?.overdue ? 'text-red-700' : 'text-slate-400'}`}>{lot.data?.overdue ?? '—'}</p>
          </div>
        </div>
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Tìm theo biển số"
          aria-label="Tìm theo biển số"
          className="w-full rounded-xl border-2 border-slate-300 px-4 py-3 font-mono text-xl uppercase tracking-wider focus:border-blue-600 focus:outline-none"
        />
        {message && <Alert tone="success">{message}</Alert>}
        {lot.isPending && <LoadingBlock />}
        {lot.isError && <QueryError error={lot.error} onRetry={() => void lot.refetch()} />}
        {lot.data?.items.length === 0 && (
          <p className="py-6 text-center text-slate-500">{fragment ? 'Không có xe nào khớp biển số vừa gõ.' : 'Bãi đang trống.'}</p>
        )}
        <ul className="divide-y divide-slate-100">
          {lot.data?.items.map((vehicle) => (
            <li key={vehicle.sessionId} className={`flex items-center justify-between gap-3 py-3 ${vehicle.overdue ? '-mx-2 rounded-lg bg-red-50 px-2' : ''}`}>
              <div className="min-w-0">
                <p className="font-mono text-xl font-bold tracking-wider">{vehicle.plateDisplay}</p>
                <p className="truncate text-sm text-slate-600">
                  {typeName(vehicle.vehicleTypeCode)} · {vehicle.fullName} ({vehicle.studentCode})
                </p>
                <p className="text-xs text-slate-500">
                  Vào {formatDateTime(vehicle.enteredAt)} · {vehicle.entryGuardName}
                </p>
              </div>
              <div className="flex shrink-0 flex-col items-end gap-2">
                <Badge tone={vehicle.overdue ? 'red' : 'gray'}>{formatDuration(vehicle.minutesParked)}</Badge>
                {vehicle.overdue && (
                  <Button size="sm" variant="danger" onClick={() => setReporting(vehicle)}>
                    Báo sự cố
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>
      {reporting && (
        <ReportIncidentDialog
          preset={{ type: 'XE_GUI_QUA_LAU', plateNumber: reporting.plateNumber }}
          onClose={() => setReporting(null)}
          onReported={() => {
            setMessage(`Đã báo sự cố xe ${reporting.plateDisplay} gửi quá lâu cho quản lý.`);
            setReporting(null);
          }}
        />
      )}
    </Card>
  );
}
