import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { errorMessage, guestApi } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { Dialog } from '../../shared/components/Dialog';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import type { GuestSearch, GuestSessionItem } from '../../shared/types/guest';
import { formatCash, formatDateTime, normalizePlate } from '../../shared/utils/format';
import { vietnamToday } from '../owner/revenue/revenueMath';

const INPUT = 'block rounded-lg border border-slate-300 bg-white p-2';
const PAGE_SIZE = 50;

export function GuestSessionsTab() {
  const today = vietnamToday();
  const [filters, setFilters] = useState<GuestSearch>({ from: today, to: today, status: '', plate: '' });
  const [offset, setOffset] = useState(0);
  const [approval, setApproval] = useState<GuestSessionItem | null>(null);
  const deferredPlate = useDeferredValue(normalizePlate(filters.plate));
  const search = { ...filters, plate: deferredPlate };
  const valid = Boolean(search.from && search.to && search.from <= search.to);
  const client = useQueryClient();
  const typeName = useVehicleTypeName();
  const report = useQuery({
    queryKey: ['guest', 'sessions', search, offset],
    queryFn: () => guestApi.search(search, offset),
    enabled: valid,
    placeholderData: keepPreviousData,
  });
  const exportCsv = useMutation({
    mutationFn: guestApi.exportSessions,
    onSuccess: (blob, selection) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `luot-xe-khach_${selection.from}_${selection.to}.csv`;
      link.click();
      URL.revokeObjectURL(url);
    },
  });
  const approve = useMutation({
    mutationFn: guestApi.approveLostTicket,
    onSuccess: () => {
      setApproval(null);
      void client.invalidateQueries({ queryKey: ['guest', 'sessions'] });
    },
  });
  const data = report.data;

  function update(key: keyof GuestSearch, value: string) {
    setFilters((previous) => ({ ...previous, [key]: value }));
    setOffset(0);
  }

  return (
    <Card title="Lượt gửi xe khách" actions={
      <Button variant="secondary" size="sm" loading={exportCsv.isPending} disabled={!valid}
        onClick={() => exportCsv.mutate({ ...search, plate: normalizePlate(filters.plate) })}>Xuất CSV</Button>
    }>
      <div className="space-y-4">
        <div className="flex flex-wrap gap-3">
          <label className="space-y-1 text-sm">Từ ngày
            <input className={INPUT} type="date" value={filters.from} max={filters.to} onChange={(e) => update('from', e.target.value)} />
          </label>
          <label className="space-y-1 text-sm">Đến ngày
            <input className={INPUT} type="date" value={filters.to} min={filters.from} onChange={(e) => update('to', e.target.value)} />
          </label>
          <label className="space-y-1 text-sm">Trạng thái
            <select className={INPUT} value={filters.status} onChange={(e) => update('status', e.target.value)}>
              <option value="">Tất cả</option><option value="DANG_GUI">Đang gửi</option><option value="DA_RA">Đã ra</option>
            </select>
          </label>
          <label className="space-y-1 text-sm">Biển số
            <input className={`${INPUT} font-mono uppercase`} value={filters.plate} maxLength={24} onChange={(e) => update('plate', e.target.value)} />
          </label>
        </div>
        {!valid && <Alert tone="warning">Vui lòng chọn khoảng ngày hợp lệ.</Alert>}
        {exportCsv.error && <Alert tone="error">{errorMessage(exportCsv.error)}</Alert>}
        {valid && report.isPending && <LoadingBlock />}
        {report.isError && <QueryError error={report.error} onRetry={() => void report.refetch()} />}
        {valid && !report.isError && data && (
          <>
            <div className="rounded-xl bg-blue-50 p-3">
              <h3 className="font-semibold">Doanh thu khách trong khoảng ngày</h3>
              <p className="text-sm text-slate-700">
                {data.revenue.payments} lượt đã thu · Tiền mặt {formatCash(data.revenue.cash)} · VietQR {formatCash(data.revenue.vietqr)}
              </p>
            </div>
            {data.items.length === 0 ? <p className="py-4 text-sm text-slate-600">Chưa có lượt phù hợp.</p> : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] text-left text-sm">
                  <thead className="bg-slate-50 text-slate-600"><tr>
                    <th className="p-2">Mã vé / Xe</th><th className="p-2">Giờ vào</th>
                    <th className="p-2">Giờ ra / Trạng thái</th><th className="p-2">Thực thu</th>
                    <th className="p-2">Thông tin khách</th>
                  </tr></thead>
                  <tbody className="divide-y divide-slate-100">
                    {data.items.map((guest) => (
                      <tr key={guest.id}>
                        <td className="p-2">
                          <p className="font-mono font-semibold">{guest.ticketCode}</p>
                          <p>{guest.plateDisplay ?? 'Không biển'} · {typeName(guest.vehicleTypeCode)}</p>
                        </td>
                        <td className="whitespace-nowrap p-2">{formatDateTime(guest.enteredAt)}</td>
                        <td className="p-2">
                          {guest.exitedAt ? formatDateTime(guest.exitedAt) : <Badge tone="blue">Đang gửi</Badge>}
                          {guest.lostTicket && <div className="mt-1"><Badge tone="amber">Mất vé</Badge></div>}
                          {!guest.plateNumber && !guest.exitedAt && (
                            <div className="mt-1">
                              {guest.lostTicketApproved ? <Badge tone="green">Đã duyệt mất vé</Badge> : (
                                <Button size="sm" variant="secondary" disabled={report.isPlaceholderData}
                                  onClick={() => { approve.reset(); setApproval(guest); }}>Duyệt mất vé</Button>
                              )}
                            </div>
                          )}
                        </td>
                        <td className="p-2">
                          {guest.amount === null ? '—' : formatCash(guest.amount)}
                          {guest.paymentMethod && <div className="text-xs text-slate-600">{guest.paymentMethod === 'VIETQR' ? 'VietQR' : 'Tiền mặt'}</div>}
                        </td>
                        <td className="max-w-64 break-words p-2">
                          {[guest.guestName, guest.guestPhone, guest.note].filter(Boolean).join(' · ') || '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {data.total > PAGE_SIZE && (
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="text-sm text-slate-600">{offset + 1}–{Math.min(offset + PAGE_SIZE, data.total)} / {data.total} lượt</span>
                <div className="flex gap-2">
                  <Button variant="secondary" size="sm" disabled={offset === 0 || report.isPlaceholderData}
                    onClick={() => setOffset(offset - PAGE_SIZE)}>Trang trước</Button>
                  <Button variant="secondary" size="sm" disabled={offset + PAGE_SIZE >= data.total || report.isPlaceholderData}
                    onClick={() => setOffset(offset + PAGE_SIZE)}>Trang sau</Button>
                </div>
              </div>
            )}
          </>
        )}
      </div>
      {approval && (
        <Dialog title={`Duyệt mất vé ${approval.ticketCode}`} onClose={() => setApproval(null)}
          footer={<Button loading={approve.isPending} onClick={() => approve.mutate(approval.id)}>Đã xác minh và duyệt</Button>}>
          <p>Đối chiếu người nhận, giấy tờ và đặc điểm xe đạp trước khi duyệt. Sau khi duyệt, cung cấp mã vé này cho bảo vệ để hoàn tất thu phí tại chốt.</p>
          {approve.error && <Alert tone="error">{errorMessage(approve.error)}</Alert>}
        </Dialog>
      )}
    </Card>
  );
}
