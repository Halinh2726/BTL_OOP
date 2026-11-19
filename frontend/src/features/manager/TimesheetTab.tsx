import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Badge } from '../../shared/components/Badge';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { formatCash, formatDateTime, formatDuration, formatTime } from '../../shared/utils/format';
import { CashDifference } from '../guard/CashDifference';

/** "yyyy-MM-dd" theo giờ Việt Nam. */
function vietnamDate(offsetDays = 0): string {
  return new Date(Date.now() + 7 * 3600_000 + offsetDays * 86_400_000).toISOString().slice(0, 10);
}

/**
 * Chấm công bảo vệ: chọn khoảng ngày (mặc định từ đầu tháng tới hôm nay) và bảo vệ → bảng tổng hợp mỗi người
 * (số ca, tổng giờ trực, lượt vào/ra, số ca khai tiền lệch) + chi tiết từng ca. Dữ liệu lấy từ nhận ca / kết thúc ca.
 */
export function TimesheetTab() {
  const today = vietnamDate();
  const [from, setFrom] = useState(`${today.slice(0, 8)}01`);
  const [to, setTo] = useState(today);
  const [guardId, setGuardId] = useState<number | undefined>(undefined);
  const guards = useQuery({ queryKey: queryKeys.guards, queryFn: api.guards });
  const valid = from <= to;
  const sheet = useQuery({
    queryKey: queryKeys.guardTimesheet(from, to, guardId),
    queryFn: () => api.guardTimesheet(from, to, guardId),
    enabled: valid,
  });

  return (
    <div className="space-y-6">
      <Card title="Chấm công bảo vệ">
        <div className="grid gap-3 sm:grid-cols-3">
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Từ ngày
            <input type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} className="block w-full rounded-lg border border-slate-300 px-3 py-2" />
          </label>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Đến ngày
            <input type="date" value={to} min={from} onChange={(event) => setTo(event.target.value)} className="block w-full rounded-lg border border-slate-300 px-3 py-2" />
          </label>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Bảo vệ
            <select
              value={guardId ?? ''}
              onChange={(event) => setGuardId(event.target.value ? Number(event.target.value) : undefined)}
              className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2"
            >
              <option value="">Tất cả</option>
              {guards.data?.map((guard) => (
                <option key={guard.id} value={guard.id}>
                  {guard.fullName} ({guard.username})
                </option>
              ))}
            </select>
          </label>
        </div>
        {!valid && <p className="mt-2 text-sm text-red-600">"Từ ngày" phải trước hoặc bằng "Đến ngày".</p>}
        <div className="mt-4">
          {sheet.isPending && valid && <LoadingBlock />}
          {sheet.isError && <QueryError error={sheet.error} onRetry={() => void sheet.refetch()} />}
          {sheet.data?.totals.length === 0 && <p className="py-6 text-center text-slate-500">Không có ca trực nào trong khoảng này.</p>}
          {sheet.data && sheet.data.totals.length > 0 && (
            <div className="-mx-5 overflow-x-auto">
              <table className="min-w-full text-sm">
                <thead className="bg-slate-50 text-left text-slate-500">
                  <tr>
                    <th className="px-5 py-2 font-medium">Bảo vệ</th>
                    <th className="px-3 py-2 text-right font-medium">Số ca</th>
                    <th className="px-3 py-2 text-right font-medium">Tổng giờ trực</th>
                    <th className="px-3 py-2 text-right font-medium">Lượt vào / ra</th>
                    <th className="px-5 py-2 text-right font-medium">Ca khai tiền lệch</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {sheet.data.totals.map((total) => (
                    <tr key={total.guardId}>
                      <td className="px-5 py-2 font-medium">{total.guardName}</td>
                      <td className="px-3 py-2 text-right">{total.shifts}</td>
                      <td className="px-3 py-2 text-right font-semibold">{formatDuration(total.workedMinutes)}</td>
                      <td className="px-3 py-2 text-right">
                        {total.checkIns} / {total.checkOuts}
                      </td>
                      <td className="px-5 py-2 text-right">
                        {total.cashMismatches > 0 ? <Badge tone="red">{total.cashMismatches} ca</Badge> : <Badge tone="green">khớp</Badge>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </Card>

      {sheet.data && sheet.data.shifts.length > 0 && (
        <Card title={`Chi tiết ${sheet.data.shifts.length} ca`}>
          <div className="-mx-5 overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-5 py-2 font-medium">Bảo vệ</th>
                  <th className="px-3 py-2 font-medium">Ca</th>
                  <th className="px-3 py-2 text-right font-medium">Giờ trực</th>
                  <th className="px-3 py-2 text-right font-medium">Vào / Ra</th>
                  <th className="px-3 py-2 text-right font-medium">Tiền nhận ca</th>
                  <th className="px-3 py-2 text-right font-medium">Tiền bàn giao</th>
                  <th className="px-5 py-2 font-medium">Người nhận ca</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {sheet.data.shifts.map((shift) => (
                  <tr key={shift.id}>
                    <td className="px-5 py-2 font-medium">{shift.guardName}</td>
                    <td className="px-3 py-2">
                      {formatDateTime(shift.startedAt).slice(0, 10)} · {formatTime(shift.startedAt)}–
                      {shift.endedAt ? formatTime(shift.endedAt) : <Badge tone="green">đang trực</Badge>}
                    </td>
                    <td className="px-3 py-2 text-right">{formatDuration(shift.workedMinutes)}</td>
                    <td className="px-3 py-2 text-right">{shift.summary ? `${shift.summary.checkIns} / ${shift.summary.checkOuts}` : '—'}</td>
                    <td className="px-3 py-2 text-right">
                      {formatCash(shift.openingCash)}
                      <CashDifference difference={shift.openingDiscrepancy} />
                    </td>
                    <td className="px-3 py-2 text-right">{shift.closingCash === null ? '—' : formatCash(shift.closingCash)}</td>
                    <td className="px-5 py-2">{shift.receivedByName ?? (shift.endedAt ? <Badge tone="amber">chưa ai nhận</Badge> : '—')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
