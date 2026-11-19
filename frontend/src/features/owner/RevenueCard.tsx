import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { RevenueReport } from '../../shared/types/api';
import { formatCash, formatNumber, formatPoints } from '../../shared/utils/format';

type Channel = 'ALL' | 'CASH_IN' | 'CASH_OUT';

function vietnamDate(): string {
  return new Date(Date.now() + 7 * 3600_000).toISOString().slice(0, 10);
}

/** "2026-11-20" -> "20/11" */
function shortDate(date: string): string {
  return `${date.slice(8, 10)}/${date.slice(5, 7)}`;
}

/** Xuất CSV ngay trên trình duyệt (UTF-8 có BOM để Excel đọc đúng tiếng Việt). */
function downloadCsv(report: RevenueReport): void {
  const rows = [
    ['Ngày', 'Lượt xe ra', 'Doanh thu (điểm)', 'Điều chỉnh (điểm)', 'Tiền mặt vào (đ)', 'Tiền mặt ra (đ)'],
    ...report.days.map((day) => [day.date, day.payments, day.revenue, day.adjustments, day.cashIn, day.cashOut]),
    ['Tổng', report.totalPayments, report.totalRevenue, report.totalAdjustments, report.totalCashIn, report.totalCashOut],
  ];
  const csv = rows.map((row) => row.join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `doanh-thu_${report.from}_${report.to}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * Doanh thu và dòng tiền theo ngày. "Theo phương thức thanh toán" = tiền vào hệ thống qua kênh nào (đổi điểm bằng tiền mặt)
 * và ra qua kênh nào (rút điểm nhận tiền mặt). Bản này mới có kênh tiền mặt.
 */
export function RevenueCard() {
  const today = vietnamDate();
  const [from, setFrom] = useState(`${today.slice(0, 8)}01`);
  const [to, setTo] = useState(today);
  const [channel, setChannel] = useState<Channel>('ALL');
  const valid = from <= to;
  const report = useQuery({ queryKey: queryKeys.ownerRevenue(from, to), queryFn: () => api.ownerRevenue(from, to), enabled: valid });
  const maxRevenue = Math.max(1, ...(report.data?.days.map((day) => day.revenue) ?? [0]));

  return (
    <Card
      title="Doanh thu theo ngày"
      actions={
        report.data && report.data.days.length > 0 && (
          <Button size="sm" variant="secondary" onClick={() => downloadCsv(report.data)}>
            Xuất CSV
          </Button>
        )
      }
    >
      <div className="space-y-4">
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
            Kênh tiền mặt
            <select value={channel} onChange={(event) => setChannel(event.target.value as Channel)} className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2">
              <option value="ALL">Vào và ra</option>
              <option value="CASH_IN">Chỉ tiền vào (đổi điểm)</option>
              <option value="CASH_OUT">Chỉ tiền ra (rút điểm)</option>
            </select>
          </label>
        </div>
        {report.isPending && valid && <LoadingBlock />}
        {report.isError && <QueryError error={report.error} onRetry={() => void report.refetch()} />}
        {report.data && (
          <>
            <dl className="grid grid-cols-2 gap-3 text-center sm:grid-cols-4">
              <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Lượt xe ra</dt><dd className="text-xl font-bold">{formatNumber(report.data.totalPayments)}</dd></div>
              <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Doanh thu</dt><dd className="text-xl font-bold">{formatPoints(report.data.totalRevenue)}</dd></div>
              <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Tiền mặt vào</dt><dd className="text-xl font-bold">{formatCash(report.data.totalCashIn)}</dd></div>
              <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Tiền mặt ra</dt><dd className="text-xl font-bold">{formatCash(report.data.totalCashOut)}</dd></div>
            </dl>
            {report.data.days.length === 0 ? (
              <p className="py-6 text-center text-slate-500">Không có phát sinh trong khoảng này.</p>
            ) : (
              <div className="-mx-5 overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead className="bg-slate-50 text-left text-slate-500">
                    <tr>
                      <th className="px-5 py-2 font-medium">Ngày</th>
                      <th className="px-3 py-2 font-medium">Doanh thu</th>
                      <th className="px-3 py-2 text-right font-medium">Lượt</th>
                      <th className="px-3 py-2 text-right font-medium">Điều chỉnh</th>
                      {channel !== 'CASH_OUT' && <th className="px-3 py-2 text-right font-medium">Tiền vào</th>}
                      {channel !== 'CASH_IN' && <th className="px-5 py-2 text-right font-medium">Tiền ra</th>}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {report.data.days.map((day) => (
                      <tr key={day.date}>
                        <td className="px-5 py-2 font-medium">{shortDate(day.date)}</td>
                        <td className="px-3 py-2">
                          <div className="flex items-center gap-2">
                            <div className="h-3 rounded bg-blue-600" style={{ width: `${Math.max(2, (day.revenue / maxRevenue) * 160)}px` }} aria-hidden="true" />
                            <span>{formatPoints(day.revenue)}</span>
                          </div>
                        </td>
                        <td className="px-3 py-2 text-right">{day.payments}</td>
                        <td className={`px-3 py-2 text-right ${day.adjustments < 0 ? 'text-red-700' : ''}`}>{day.adjustments ? formatNumber(day.adjustments) : '—'}</td>
                        {channel !== 'CASH_OUT' && <td className="px-3 py-2 text-right">{formatCash(day.cashIn)}</td>}
                        {channel !== 'CASH_IN' && <td className="px-5 py-2 text-right">{formatCash(day.cashOut)}</td>}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </div>
    </Card>
  );
}
