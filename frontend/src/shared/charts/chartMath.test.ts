import { describe, expect, it } from 'vitest';
import { axisScale, labelEvery, niceStep, tickLabel } from './chartMath';

describe('chartMath — trục biểu đồ', () => {
  it('niceStep và axisScale cho số tròn; một trục chung cho cột lên và xuống', () => {
    expect(niceStep(24000)).toBe(5000);
    expect(niceStep(7)).toBe(2);
    expect(axisScale(24000)).toEqual({ min: 0, max: 25000, ticks: [0, 5000, 10000, 15000, 20000, 25000] });
    const mixed = axisScale(50000, 3000);
    expect(mixed.min).toBeLessThan(0);
    expect(mixed.ticks).toContain(0);
    expect(mixed.max).toBeGreaterThanOrEqual(50000);
    expect(axisScale(0).max).toBeGreaterThan(0); // không có dữ liệu: vẫn vẽ được trục
  });

  it('số lẻ (trung bình lượt xe / giờ): vạch không bị sai số dấu phẩy động', () => {
    const scale = axisScale(0.7, 0.3);
    expect(scale.ticks).toContain(0);
    expect(scale.ticks.every((tick) => Number(tick.toFixed(6)) === tick)).toBe(true);
    expect(scale.max).toBeGreaterThanOrEqual(0.7);
    expect(scale.min).toBeLessThanOrEqual(-0.3);
  });

  it('nhãn trục: đầy đủ dưới 10.000, rút gọn khi trục chạm 10.000', () => {
    expect(tickLabel(2000, 8000)).toBe('2.000');
    expect(tickLabel(0.5, 2)).toBe('0,5');
    expect(tickLabel(20000, 25000)).not.toBe('20.000');
    expect(labelEvery(50)).toBe(1);
    expect(labelEvery(9)).toBe(5);
  });
});
