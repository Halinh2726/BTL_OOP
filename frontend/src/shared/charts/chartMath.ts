/** Các hàm thuần cho trục biểu đồ — không phụ thuộc React, kiểm thử bằng vitest. */

/** Bước chia trục "tròn" (1, 2, 2.5, 5 × 10^k) sao cho khoảng `span` được chia thành khoảng `count` vạch. */
export function niceStep(span: number, count = 5): number {
  if (span <= 0) return 1;
  const raw = span / count;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const nice = [1, 2, 2.5, 5, 10].find((m) => m * magnitude >= raw) ?? 10;
  return nice * magnitude;
}

/** Bỏ sai số dấu phẩy động (0.1 + 0.2 = 0.30000000000000004). */
function clean(value: number): number {
  return Number(value.toFixed(6));
}

/**
 * Thang đo một trục duy nhất cho cột hướng lên (`maxUp`) và hướng xuống (`maxDown`, ví dụ tiền ra).
 * Trả về đáy (≤ 0), đỉnh và các vạch chia — đều là số tròn.
 */
export function axisScale(maxUp: number, maxDown = 0): { min: number; max: number; ticks: number[] } {
  const up = Math.max(0, maxUp);
  const down = Math.max(0, maxDown);
  const step = up + down === 0 ? 1000 : niceStep(up + down, up > 0 && down > 0 ? 6 : 5);
  const max = up === 0 && down > 0 ? 0 : clean(Math.max(step, Math.ceil(clean(up / step)) * step));
  const min = down === 0 ? 0 : clean(-Math.ceil(clean(down / step)) * step);
  const ticks: number[] = [];
  for (let i = 0; clean(min + i * step) <= max; i += 1) ticks.push(clean(min + i * step));
  return { min, max, ticks };
}

const compact = new Intl.NumberFormat('vi-VN', { notation: 'compact', maximumFractionDigits: 1 });
const full = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 });

/** Nhãn vạch trục: đầy đủ dưới 10.000, rút gọn khi trục chạm 10.000 (một kiểu cho cả trục). */
export function tickLabel(value: number, axisMax: number): string {
  return Math.abs(axisMax) >= 10_000 ? compact.format(value) : full.format(value);
}

/** Cứ bao nhiêu cột thì in một nhãn, để nhãn (~40px) không đè nhau. */
export function labelEvery(slotWidth: number, minGap = 40): number {
  return Math.max(1, Math.ceil(minGap / Math.max(1, slotWidth)));
}
