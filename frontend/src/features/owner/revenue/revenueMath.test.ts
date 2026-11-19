import { describe, expect, it } from 'vitest';
import { axisScale, eachDay, fillDays, labelEvery, longDate, matchingPreset, niceStep, presetRange, tickLabel, vietnamToday } from './revenueMath';

describe('revenueMath — tính toán cho biểu đồ doanh thu', () => {
  it('eachDay gồm cả hai đầu, qua được cuối tháng và năm nhuận', () => {
    expect(eachDay('2028-02-27', '2028-03-01')).toEqual(['2028-02-27', '2028-02-28', '2028-02-29', '2028-03-01']);
    expect(eachDay('2026-11-19', '2026-11-19')).toEqual(['2026-11-19']);
    expect(eachDay('2026-11-20', '2026-11-19')).toEqual([]);
  });

  it('fillDays điền 0 cho ngày không có phát sinh, giữ nguyên số liệu ngày có', () => {
    const filled = fillDays([{ date: '2026-11-18', payments: 1, revenue: 5000, adjustments: 0, cashIn: 0, cashOut: 0 }], '2026-11-17', '2026-11-19');
    expect(filled.map((day) => [day.date, day.revenue])).toEqual([['2026-11-17', 0], ['2026-11-18', 5000], ['2026-11-19', 0]]);
  });

  it('presetRange: 7 ngày, 30 ngày, tháng này, tháng trước (kể cả qua năm)', () => {
    expect(presetRange('7D', '2026-11-19')).toEqual({ from: '2026-11-13', to: '2026-11-19' });
    expect(presetRange('30D', '2026-11-19')).toEqual({ from: '2026-10-21', to: '2026-11-19' });
    expect(presetRange('THIS_MONTH', '2026-11-19')).toEqual({ from: '2026-11-01', to: '2026-11-19' });
    expect(presetRange('LAST_MONTH', '2027-01-05')).toEqual({ from: '2026-12-01', to: '2026-12-31' });
    expect(presetRange('LAST_MONTH', '2028-03-10')).toEqual({ from: '2028-02-01', to: '2028-02-29' });
    expect(matchingPreset('2026-11-01', '2026-11-19', '2026-11-19')).toBe('THIS_MONTH');
    expect(matchingPreset('2026-11-02', '2026-11-19', '2026-11-19')).toBeNull();
  });

  it('vietnamToday dùng giờ Việt Nam: 18:30 UTC đã là ngày hôm sau', () => {
    expect(vietnamToday(Date.UTC(2026, 10, 18, 18, 30))).toBe('2026-11-19');
  });

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

  it('nhãn trục: đầy đủ dưới 10.000, rút gọn khi trục chạm 10.000', () => {
    expect(tickLabel(2000, 8000)).toBe('2.000');
    expect(tickLabel(20000, 25000)).not.toBe('20.000');
    expect(labelEvery(50)).toBe(1);
    expect(labelEvery(9)).toBe(5);
  });

  it('longDate có thứ trong tuần', () => {
    expect(longDate('2026-11-19')).toBe('T5, 19/11/2026');
    expect(longDate('2026-11-22')).toBe('CN, 22/11/2026');
  });
});
