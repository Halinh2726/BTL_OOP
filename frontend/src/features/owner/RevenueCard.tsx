import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { RevenueReport } from '../../shared/types/api';
import { formatCash, formatNumber, formatPoints } from '../../shared/utils/format';
import { BarChart, CHART_COLORS, ChartLegend, type BarSeries } from '../../shared/charts/BarChart';
import { RANGE_PRESETS, fillDays, longDate, matchingPreset, presetRange, shortDate, vietnamToday } from './revenue/revenueMath';

type Channel = 'ALL' | 'CASH_IN' | 'CASH_OUT';

/**
 * Màu theo ĐỐI TƯỢNG, cố định trên cả trang (không đổi khi lọc): doanh thu = xanh dương, tiền ra = cam, tiền vào =
 * xanh ngọc — 3 ô đầu của bảng màu phân loại, đã kiểm tra phân biệt được với người mù màu.
 */
const COLORS = { revenue: CHART_COLORS.primary, cashOut: CHART_COLORS.secondary, cashIn: CHART_COLORS.tertiary };

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
 * Doanh thu và dòng tiền theo ngày, có biểu đồ. Bộ lọc (khoảng ngày, kênh tiền mặt) nằm một hàng trên cùng và áp
 * dụng cho mọi số liệu bên dưới. Bảng số liệu đầy đủ vẫn có ở mục "Xem dạng bảng".
 */
export function RevenueCard() {
  const today = vietnamToday();
  const initial = presetRange('THIS_MONTH', today);
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [channel, setChannel] = useState<Channel>('ALL');
  const valid = from <= to;
  const report = useQuery({
    queryKey: queryKeys.ownerRevenue(from, to),
    queryFn: () => api.ownerRevenue(from, to),
    enabled: valid,
    placeholderData: keepPreviousData, // đổi khoảng ngày: giữ biểu đồ cũ (mờ đi) thay vì nháy trắng
  });
  const preset = matchingPreset(from, to, today);
  const data = report.data;
  const days = data ? fillDays(data.days, data.from, data.to) : [];
  const dates = days.map((day) => day.date);

  const revenueSeries: BarSeries[] = [
    { id: 'revenue', label: 'doanh thu', color: COLORS.revenue, direction: 'up', values: days.map((d) => d.revenue), format: formatPoints },
  ];
  const cashSeries: BarSeries[] = [
    { id: 'cashIn', label: 'tiền vào (đổi điểm)', color: COLORS.cashIn, direction: 'up' as const, values: days.map((d) => d.cashIn), format: formatCash },
    { id: 'cashOut', label: 'tiền ra (rút điểm)', color: COLORS.cashOut, direction: 'down' as const, values: days.map((d) => d.cashOut), format: formatCash },
  ].filter((s) => channel === 'ALL' || (channel === 'CASH_IN' ? s.id === 'cashIn' : s.id === 'cashOut'));

  return (
    <Card
      title="Doanh thu theo ngày"
      actions={
        data && data.days.length > 0 && (
          <Button size="sm" variant="secondary" onClick={() => downloadCsv(data)}>
            Xuất CSV
          </Button>
        )
      }
    >
      <div className="space-y-5">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-wrap gap-1.5" role="group" aria-label="Chọn nhanh khoảng ngày">
            {RANGE_PRESETS.map((item) => (
              <button
                key={item.id}
                type="button"
                aria-pressed={preset === item.id}
                onClick={() => {
                  const range = presetRange(item.id, today);
                  setFrom(range.from);
                  setTo(range.to);
                }}
                className={`rounded-full px-3 py-1.5 text-sm font-semibold ring-1 ring-inset ${
                  preset === item.id ? 'bg-blue-700 text-white ring-blue-700' : 'bg-white text-slate-600 ring-slate-300 hover:bg-slate-50'
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Từ ngày
            <input type="date" value={from} max={to} onChange={(event) => setFrom(event.target.value)} className="block rounded-lg border border-slate-300 px-3 py-1.5" />
          </label>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Đến ngày
            <input type="date" value={to} min={from} max={today} onChange={(event) => setTo(event.target.value)} className="block rounded-lg border border-slate-300 px-3 py-1.5" />
          </label>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Kênh tiền mặt
            <select value={channel} onChange={(event) => setChannel(event.target.value as Channel)} className="block rounded-lg border border-slate-300 bg-white px-3 py-1.5">
              <option value="ALL">Vào và ra</option>
              <option value="CASH_IN">Chỉ tiền vào (đổi điểm)</option>
              <option value="CASH_OUT">Chỉ tiền ra (rút điểm)</option>
            </select>
          </label>
        </div>

        {!valid && <p className="text-sm text-red-700">"Từ ngày" phải trước hoặc bằng "Đến ngày".</p>}
        {report.isPending && valid && <LoadingBlock />}
        {report.isError && <QueryError error={report.error} onRetry={() => void report.refetch()} />}

        {data && (
          <div className={`space-y-6 transition-opacity ${report.isPlaceholderData ? 'opacity-50' : ''}`}>
            <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Doanh thu</dt><dd className="text-xl font-bold text-slate-900">{formatPoints(data.totalRevenue)}</dd></div>
              <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Lượt xe ra</dt><dd className="text-xl font-bold text-slate-900">{formatNumber(data.totalPayments)}</dd></div>
              <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Tiền mặt vào</dt><dd className="text-xl font-bold text-slate-900">{formatCash(data.totalCashIn)}</dd></div>
              <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Tiền mặt ra</dt><dd className="text-xl font-bold text-slate-900">{formatCash(data.totalCashOut)}</dd></div>
            </dl>

            {data.days.length === 0 ? (
              <p className="py-6 text-center text-slate-500">Không có phát sinh trong khoảng này.</p>
            ) : (
              <>
                <section className="space-y-2">
                  <div>
                    <h3 className="font-semibold text-slate-900">Doanh thu phí gửi xe</h3>
                    <p className="text-sm text-slate-500">Điểm thu được mỗi ngày, {shortDate(data.from)} – {shortDate(data.to)}</p>
                  </div>
                  <BarChart
                    keys={dates}
                    keyLabel={shortDate}
                    keyTitle={longDate}
                    series={revenueSeries}
                    labelMaxOf="revenue"
                    ariaLabel={`Biểu đồ cột doanh thu theo ngày, tổng ${formatPoints(data.totalRevenue)}`}
                    details={(index) => {
                      const day = days[index];
                      if (!day) return [];
                      return [
                        { label: 'lượt xe ra', value: formatNumber(day.payments) },
                        ...(day.adjustments ? [{ label: 'điều chỉnh', value: formatNumber(day.adjustments) }] : []),
                      ];
                    }}
                  />
                </section>

                <section className="space-y-2">
                  <div className="flex flex-wrap items-end justify-between gap-2">
                    <div>
                      <h3 className="font-semibold text-slate-900">Dòng tiền mặt</h3>
                      <p className="text-sm text-slate-500">Tiền vào ở trên đường 0, tiền ra ở dưới</p>
                    </div>
                    <ChartLegend
                      items={cashSeries.map((s) => ({ label: s.id === 'cashIn' ? 'Tiền vào (đổi điểm)' : 'Tiền ra (rút điểm)', color: s.color }))}
                    />
                  </div>
                  <BarChart
                    keys={dates}
                    keyLabel={shortDate}
                    keyTitle={longDate}
                    series={cashSeries}
                    height={200}
                    ariaLabel={`Biểu đồ dòng tiền mặt theo ngày, vào ${formatCash(data.totalCashIn)}, ra ${formatCash(data.totalCashOut)}`}
                    details={(index) => {
                      const day = days[index];
                      return channel === 'ALL' && day ? [{ label: 'chênh lệch', value: formatCash(day.cashIn - day.cashOut) }] : [];
                    }}
                  />
                </section>

                <details className="group rounded-xl ring-1 ring-slate-200">
                  <summary className="cursor-pointer select-none px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50">
                    Xem dạng bảng ({data.days.length} ngày có phát sinh)
                  </summary>
                  <div className="overflow-x-auto border-t border-slate-200">
                    <table className="min-w-full text-sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
                      <thead className="bg-slate-50 text-left text-slate-500">
                        <tr>
                          <th className="px-4 py-2 font-medium">Ngày</th>
                          <th className="px-3 py-2 text-right font-medium">Doanh thu</th>
                          <th className="px-3 py-2 text-right font-medium">Lượt</th>
                          <th className="px-3 py-2 text-right font-medium">Điều chỉnh</th>
                          {channel !== 'CASH_OUT' && <th className="px-3 py-2 text-right font-medium">Tiền vào</th>}
                          {channel !== 'CASH_IN' && <th className="px-4 py-2 text-right font-medium">Tiền ra</th>}
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-100">
                        {data.days.map((day) => (
                          <tr key={day.date}>
                            <td className="px-4 py-2 font-medium">{shortDate(day.date)}</td>
                            <td className="px-3 py-2 text-right">{formatPoints(day.revenue)}</td>
                            <td className="px-3 py-2 text-right">{day.payments}</td>
                            <td className={`px-3 py-2 text-right ${day.adjustments < 0 ? 'text-red-700' : ''}`}>{day.adjustments ? formatNumber(day.adjustments) : '—'}</td>
                            {channel !== 'CASH_OUT' && <td className="px-3 py-2 text-right">{formatCash(day.cashIn)}</td>}
                            {channel !== 'CASH_IN' && <td className="px-4 py-2 text-right">{formatCash(day.cashOut)}</td>}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </details>
              </>
            )}
          </div>
        )}
      </div>
    </Card>
  );
}
