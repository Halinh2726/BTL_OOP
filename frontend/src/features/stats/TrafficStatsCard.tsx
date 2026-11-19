import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { BarChart, CHART_COLORS, ChartLegend, type BarSeries } from '../../shared/charts/BarChart';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { formatNumber, hourRange } from '../../shared/utils/format';

const DAY_OPTIONS = [7, 30, 90] as const;
const decimal = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const perHour = (value: number) => `${decimal.format(value)} lượt`;

/**
 * Lượt xe vào / ra theo từng giờ trong ngày (trung bình N ngày) để thấy giờ cao điểm và xếp người trực.
 * Dùng chung cho quản lý và chủ bãi xe. Xe vào mọc lên trên đường 0, xe ra mọc xuống — một trục.
 */
export function TrafficStatsCard() {
  const [days, setDays] = useState<(typeof DAY_OPTIONS)[number]>(30);
  const report = useQuery({
    queryKey: queryKeys.trafficStats(days),
    queryFn: () => api.trafficStats(days),
    placeholderData: keepPreviousData,
  });
  const data = report.data;
  const hours = data?.hours ?? [];
  const series: BarSeries[] = [
    { id: 'in', label: 'xe vào', color: CHART_COLORS.primary, direction: 'up', values: hours.map((h) => h.averageEntries), format: perHour },
    { id: 'out', label: 'xe ra', color: CHART_COLORS.secondary, direction: 'down', values: hours.map((h) => h.averageExits), format: perHour },
  ];

  return (
    <Card
      title="Lượt xe theo giờ"
      actions={
        <div className="flex gap-1.5" role="group" aria-label="Số ngày thống kê">
          {DAY_OPTIONS.map((option) => (
            <button
              key={option}
              type="button"
              aria-pressed={days === option}
              onClick={() => setDays(option)}
              className={`rounded-full px-3 py-1 text-sm font-semibold ring-1 ring-inset ${
                days === option ? 'bg-blue-700 text-white ring-blue-700' : 'bg-white text-slate-600 ring-slate-300 hover:bg-slate-50'
              }`}
            >
              {option} ngày
            </button>
          ))}
        </div>
      }
    >
      {report.isPending && <LoadingBlock />}
      {report.isError && <QueryError error={report.error} onRetry={() => void report.refetch()} />}
      {data && (
        <div className={`space-y-5 transition-opacity ${report.isPlaceholderData ? 'opacity-50' : ''}`}>
          <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Xe đang trong bãi</dt><dd className="text-xl font-bold text-slate-900">{formatNumber(data.parkedNow)}</dd></div>
            <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Lượt vào / ngày (TB)</dt><dd className="text-xl font-bold text-slate-900">{decimal.format(data.averageEntriesPerDay)}</dd></div>
            <div className="rounded-xl bg-slate-50 p-3"><dt className="text-xs text-slate-500">Tổng lượt vào {data.days} ngày</dt><dd className="text-xl font-bold text-slate-900">{formatNumber(data.totalEntries)}</dd></div>
            <div className="rounded-xl bg-slate-50 p-3">
              <dt className="text-xs text-slate-500">Giờ cao điểm</dt>
              <dd className="text-xl font-bold text-slate-900">{data.peakHours.length ? `${String(data.peakHours[0]).padStart(2, '0')}:00` : '—'}</dd>
            </div>
          </dl>

          {data.totalEntries === 0 ? (
            <p className="py-6 text-center text-slate-500">Chưa có lượt xe nào trong {data.days} ngày qua.</p>
          ) : (
            <section className="space-y-2">
              <div className="flex flex-wrap items-end justify-between gap-2">
                <p className="text-sm text-slate-500">
                  Trung bình mỗi ngày từ {data.from.split('-').reverse().join('/')} đến {data.to.split('-').reverse().join('/')}.
                  {data.peakHours.length > 1 && ` Đông nhất: ${data.peakHours.map(hourRange).join(', ')}.`}
                </p>
                <ChartLegend items={[{ label: 'Xe vào', color: CHART_COLORS.primary }, { label: 'Xe ra', color: CHART_COLORS.secondary }]} />
              </div>
              <BarChart
                keys={hours.map((h) => String(h.hour))}
                keyLabel={(key) => `${key}h`}
                keyTitle={(key) => `${hourRange(Number(key))} · trung bình/ngày`}
                series={series}
                anchorLabels="start"
                labelMaxOf="in"
                ariaLabel={`Biểu đồ lượt xe vào và ra theo giờ, trung bình ${data.days} ngày`}
                details={(index) => {
                  const hour = hours[index];
                  return hour ? [{ label: 'vào hôm nay', value: formatNumber(hour.todayEntries) }, { label: 'ra hôm nay', value: formatNumber(hour.todayExits) }] : [];
                }}
              />
              <details className="rounded-xl ring-1 ring-slate-200">
                <summary className="cursor-pointer select-none px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50">Xem dạng bảng</summary>
                <div className="overflow-x-auto border-t border-slate-200">
                  <table className="min-w-full text-sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
                    <thead className="bg-slate-50 text-left text-slate-500">
                      <tr>
                        <th className="px-4 py-2 font-medium">Khung giờ</th>
                        <th className="px-3 py-2 text-right font-medium">Vào (TB)</th>
                        <th className="px-3 py-2 text-right font-medium">Ra (TB)</th>
                        <th className="px-3 py-2 text-right font-medium">Vào hôm nay</th>
                        <th className="px-4 py-2 text-right font-medium">Ra hôm nay</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {hours.filter((h) => h.averageEntries + h.averageExits + h.todayEntries + h.todayExits > 0).map((h) => (
                        <tr key={h.hour}>
                          <td className="px-4 py-2 font-medium">{hourRange(h.hour)}</td>
                          <td className="px-3 py-2 text-right">{decimal.format(h.averageEntries)}</td>
                          <td className="px-3 py-2 text-right">{decimal.format(h.averageExits)}</td>
                          <td className="px-3 py-2 text-right">{h.todayEntries}</td>
                          <td className="px-4 py-2 text-right">{h.todayExits}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </details>
            </section>
          )}
        </div>
      )}
    </Card>
  );
}
