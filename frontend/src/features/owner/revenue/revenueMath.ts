import type { RevenueDay } from '../../../shared/types/api';

/** Các hàm thuần (không React) về ngày cho biểu đồ doanh thu — tách riêng để kiểm thử bằng vitest. */

const DAY_MS = 86_400_000;

function toUtc(date: string): number {
  return Date.UTC(Number(date.slice(0, 4)), Number(date.slice(5, 7)) - 1, Number(date.slice(8, 10)));
}

function toIso(utcMs: number): string {
  return new Date(utcMs).toISOString().slice(0, 10);
}

/** Ngày hôm nay theo giờ Việt Nam (UTC+7), dạng "yyyy-mm-dd". */
export function vietnamToday(now: number = Date.now()): string {
  return toIso(now + 7 * 3600_000);
}

/** Mọi ngày từ `from` đến `to` (gồm cả hai đầu). */
export function eachDay(from: string, to: string): string[] {
  const days: string[] = [];
  for (let t = toUtc(from); t <= toUtc(to); t += DAY_MS) days.push(toIso(t));
  return days;
}

/** Máy chủ chỉ trả những ngày có phát sinh — biểu đồ theo thời gian cần đủ mọi ngày, ngày trống = 0. */
export function fillDays(days: RevenueDay[], from: string, to: string): RevenueDay[] {
  const byDate = new Map(days.map((day) => [day.date, day]));
  return eachDay(from, to).map(
    (date) => byDate.get(date) ?? { date, payments: 0, revenue: 0, adjustments: 0, cashIn: 0, cashOut: 0 },
  );
}

export type RangePreset = '7D' | '30D' | 'THIS_MONTH' | 'LAST_MONTH';

export const RANGE_PRESETS: Array<{ id: RangePreset; label: string }> = [
  { id: '7D', label: '7 ngày' },
  { id: '30D', label: '30 ngày' },
  { id: 'THIS_MONTH', label: 'Tháng này' },
  { id: 'LAST_MONTH', label: 'Tháng trước' },
];

/** Khoảng ngày của một nút chọn nhanh, tính theo `today`. */
export function presetRange(preset: RangePreset, today: string): { from: string; to: string } {
  const t = toUtc(today);
  switch (preset) {
    case '7D':
      return { from: toIso(t - 6 * DAY_MS), to: today };
    case '30D':
      return { from: toIso(t - 29 * DAY_MS), to: today };
    case 'THIS_MONTH':
      return { from: `${today.slice(0, 8)}01`, to: today };
    case 'LAST_MONTH': {
      const firstOfThisMonth = toUtc(`${today.slice(0, 8)}01`);
      const lastOfPrevious = toIso(firstOfThisMonth - DAY_MS);
      return { from: `${lastOfPrevious.slice(0, 8)}01`, to: lastOfPrevious };
    }
  }
}

/** Nút chọn nhanh nào khớp đúng khoảng đang chọn (để tô đậm), hoặc null nếu là khoảng tự chọn. */
export function matchingPreset(from: string, to: string, today: string): RangePreset | null {
  const found = RANGE_PRESETS.find(({ id }) => {
    const range = presetRange(id, today);
    return range.from === from && range.to === to;
  });
  return found?.id ?? null;
}

const WEEKDAYS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

/** "2026-11-19" -> "T5, 19/11/2026" */
export function longDate(date: string): string {
  const weekday = WEEKDAYS[new Date(toUtc(date)).getUTCDay()];
  return `${weekday}, ${date.slice(8, 10)}/${date.slice(5, 7)}/${date.slice(0, 4)}`;
}

/** "2026-11-19" -> "19/11" */
export function shortDate(date: string): string {
  return `${date.slice(8, 10)}/${date.slice(5, 7)}`;
}
