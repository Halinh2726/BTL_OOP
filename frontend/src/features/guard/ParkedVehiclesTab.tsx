import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { api, guestApi, isMockMode } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import type { ParkedVehicleItem } from '../../shared/types/api';
import type { ParkedGuest } from '../../shared/types/guest';
import { formatDateTime, formatDuration, normalizePlate } from '../../shared/utils/format';
import { ReportIncidentDialog } from './ReportIncidentDialog';

type Filter = 'all' | 'student' | 'guest';
const FILTERS: Array<[Filter, string]> = [['all', 'Tất cả'], ['student', 'Sinh viên'], ['guest', 'Khách']];
type Row = { guest: true; vehicle: ParkedGuest } | { guest: false; vehicle: ParkedVehicleItem };

/**
 * Đợt 2 — xe đang trong bãi: tổng số xe, tìm theo biển số, gửi lâu nhất lên đầu; xe gửi quá 24 giờ tô đỏ
 * kèm nút báo sự cố "xe gửi quá lâu". Tự làm mới mỗi 30 giây.
 */
export function ParkedVehiclesTab() {
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const fragment = useDeferredValue(normalizePlate(search));
  const [reporting, setReporting] = useState<ParkedVehicleItem | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const typeName = useVehicleTypeName();
  const lot = useQuery({
    queryKey: queryKeys.parkedVehicles(fragment),
    queryFn: () => api.parkedVehicles(fragment),
    refetchInterval: 30_000,
    placeholderData: (previous) => previous,
    enabled: filter !== 'guest',
  });
  const guests = useInfiniteQuery({
    queryKey: ['guard', 'guest', 'parked', fragment],
    queryFn: ({ pageParam }) => guestApi.parked(fragment, pageParam),
    initialPageParam: 0,
    getNextPageParam: (page, pages) => {
      const loaded = pages.reduce((total, item) => total + item.items.length, 0);
      return loaded < page.total && page.items.length > 0 ? loaded : undefined;
    },
    enabled: filter !== 'student' && !isMockMode,
    refetchInterval: 30_000,
  });
  const showStudents = filter !== 'guest';
  const showGuests = filter !== 'student' && !isMockMode;
  const guestCount = guests.data?.pages[0];
  const total = (showStudents ? lot.data?.total ?? 0 : 0) + (showGuests ? guestCount?.total ?? 0 : 0);
  const overdue = (showStudents ? lot.data?.overdue ?? 0 : 0) + (showGuests ? guestCount?.overdue ?? 0 : 0);
  const ready = (!showStudents || Boolean(lot.data)) && (!showGuests || Boolean(guests.data));
  const rows: Row[] = [
    ...(showStudents ? (lot.data?.items ?? []).map((vehicle) => ({ guest: false as const, vehicle })) : []),
    ...(showGuests ? (guests.data?.pages ?? []).flatMap((page) =>
      page.items.map((vehicle) => ({ guest: true as const, vehicle }))) : []),
  ];
  rows.sort((a, b) => Date.parse(a.vehicle.enteredAt) - Date.parse(b.vehicle.enteredAt));

  return (
    <Card>
      <div className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-xl bg-blue-50 p-4 text-center">
            <p className="text-sm text-blue-700">Xe đang trong bãi</p>
            <p className="text-4xl font-bold text-blue-900">{ready ? total : '—'}</p>
          </div>
          <div className={`rounded-xl p-4 text-center ${overdue ? 'bg-red-50' : 'bg-slate-50'}`}>
            <p className={`text-sm ${overdue ? 'text-red-700' : 'text-slate-500'}`}>Gửi quá 24 giờ</p>
            <p className={`text-4xl font-bold ${overdue ? 'text-red-700' : 'text-slate-400'}`}>{ready ? overdue : '—'}</p>
          </div>
        </div>
        {!isMockMode && (
          <div className="flex gap-2" role="group" aria-label="Lọc xe trong bãi">
            {FILTERS.map(([id, label]) => (
              <Button key={id} size="sm" variant={filter === id ? 'primary' : 'secondary'}
                aria-pressed={filter === id} onClick={() => setFilter(id)}>{label}</Button>
            ))}
          </div>
        )}
        <input
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Tìm theo biển số"
          aria-label="Tìm theo biển số"
          className="w-full rounded-xl border-2 border-slate-300 px-4 py-3 font-mono text-xl uppercase tracking-wider focus:border-blue-600 focus:outline-none"
        />
        {message && <Alert tone="success">{message}</Alert>}
        {((showStudents && lot.isPending) || (showGuests && guests.isPending)) && <LoadingBlock />}
        {showStudents && lot.isError && <QueryError error={lot.error} onRetry={() => void lot.refetch()} />}
        {showGuests && guests.isError && <QueryError error={guests.error} onRetry={() => void guests.refetch()} />}
        {ready && rows.length === 0 && (
          <p className="py-6 text-center text-slate-500">{fragment ? 'Không có xe nào khớp biển số vừa gõ.' : 'Bãi đang trống.'}</p>
        )}
        <ul className="divide-y divide-slate-100">
          {rows.map((item) => {
            const { vehicle } = item;
            const minutes = item.guest
              ? Math.max(0, Math.floor((Date.now() - Date.parse(item.vehicle.enteredAt)) / 60_000))
              : item.vehicle.minutesParked;
            const isOverdue = item.guest ? minutes >= 1440 : item.vehicle.overdue;
            return (
            <li key={`${item.guest}-${vehicle.sessionId}`} className={`flex items-center justify-between gap-3 py-3 ${isOverdue ? '-mx-2 rounded-lg bg-red-50 px-2' : ''}`}>
              <div className="min-w-0">
                <p className="font-mono text-xl font-bold tracking-wider">{vehicle.plateDisplay ?? 'Không biển'}</p>
                <p className="truncate text-sm text-slate-600">
                  {typeName(vehicle.vehicleTypeCode)}
                  {!item.guest && ` · ${item.vehicle.fullName} (${item.vehicle.studentCode})`}
                </p>
                <p className="text-xs text-slate-500">
                  Vào {formatDateTime(vehicle.enteredAt)}{!item.guest && ` · ${item.vehicle.entryGuardName}`}
                </p>
              </div>
              <div className="flex shrink-0 flex-col items-end gap-2">
                {item.guest && <Badge tone="amber">Khách</Badge>}
                <Badge tone={isOverdue ? 'red' : 'gray'}>{formatDuration(minutes)}</Badge>
                {!item.guest && isOverdue && (
                  <Button size="sm" variant="danger" onClick={() => setReporting(item.vehicle)}>
                    Báo sự cố
                  </Button>
                )}
              </div>
            </li>
            );
          })}
        </ul>
        {showGuests && guests.hasNextPage && (
          <Button size="sm" variant="secondary" loading={guests.isFetchingNextPage} onClick={() => void guests.fetchNextPage()}>
            Xem thêm xe khách
          </Button>
        )}
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
