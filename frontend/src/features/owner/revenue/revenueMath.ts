import type { RevenueDay } from '../../../shared/types/api';

/** Các hàm thuần (không React) cho biểu đồ doanh thu — tách riêng để kiểm thử bằng vitest. */

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

/** Bước chia trục "tròn" (1, 2, 2.5, 5 × 10^k) sao cho khoảng `span` được chia thành khoảng `count` vạch. */
export function niceStep(span: number, count = 5): number {
  if (span <= 0) return 1;
  const raw = span / count;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const nice = [1, 2, 2.5, 5, 10].find((m) => m * magnitude >= raw) ?? 10;
  return nice * magnitude;
}

/**
 * Thang đo một trục duy nhất cho cột hướng lên (`maxUp`) và hướng xuống (`maxDown`, ví dụ tiền ra).
 * Trả về đáy (≤ 0), đỉnh và các vạch chia — đều là số tròn.
 */
export function axisScale(maxUp: number, maxDown = 0): { min: number; max: number; ticks: number[] } {
  const up = Math.max(0, maxUp);
  const down = Math.max(0, maxDown);
  const step = up + down === 0 ? 1000 : niceStep(up + down, up > 0 && down > 0 ? 6 : 5);
  const max = up === 0 && down > 0 ? 0 : Math.max(step, Math.ceil(up / step) * step);
  const min = down === 0 ? 0 : -Math.ceil(down / step) * step;
  const ticks: number[] = [];
  for (let value = min; value <= max + step / 2; value += step) ticks.push(Math.round(value));
  return { min, max, ticks };
}

const compact = new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 });
const full = new Intl.NumberFormat('vi-VN');

/** Nhãn vạch trục: đầy đủ dưới 10.000, rút gọn khi trục chạm 10.000 (một kiểu cho cả trục). */
export function tickLabel(value: number, axisMax: number): string {
  return Math.abs(axisMax) >= 10_000 ? compact.format(value) : full.format(value);
}

/** Cứ bao nhiêu ngày thì in một nhãn ngày, để nhãn (~40px) không đè nhau. */
export function labelEvery(slotWidth: number, minGap = 40): number {
  return Math.max(1, Math.ceil(minGap / Math.max(1, slotWidth)));
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
