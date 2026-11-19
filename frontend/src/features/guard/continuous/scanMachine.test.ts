import { describe, expect, it } from 'vitest';
import { COUNTDOWN_MS, INITIAL, RecentPlates, autoNextDelay, reduce, type ScanEvent, type ScanState, type Verdict } from './scanMachine';

const ALLOWED: Verdict = {
  plate: '29A12345', plateDisplay: '29A-123.45', allowed: true, reason: null, fullName: 'Nguyễn Văn A',
  studentCode: 'B21DCCN001', vehicleTypeCode: 'XE_MAY', detail: null, shortfall: null,
};
const DENIED: Verdict = { ...ALLOWED, allowed: false, reason: 'INSUFFICIENT_POINTS', shortfall: 1000 };

function run(events: ScanEvent[], from: ScanState = INITIAL): ScanState {
  return events.reduce(reduce, from);
}

describe('quét liên tục — máy trạng thái', () => {
  it('quét → được qua → đếm ngược hết 2 giây thì TỰ xác nhận, không cần chạm', () => {
    const counting = run([{ type: 'DETECTED', plate: '29A12345' }, { type: 'PREVIEWED', verdict: ALLOWED, key: 'k1' }]);
    expect(counting).toMatchObject({ kind: 'countdown', remainingMs: COUNTDOWN_MS });

    const ticks = Array.from({ length: COUNTDOWN_MS / 100 }, () => ({ type: 'TICK', elapsedMs: 100 }) as const);
    const confirming = run(ticks, counting);
    expect(confirming).toEqual({ kind: 'confirming', verdict: ALLOWED, key: 'k1' });

    const done = reduce(confirming, { type: 'CONFIRMED', outcome: { title: 'Đã cho xe vào', lines: [] } });
    expect(done.kind).toBe('done');
    expect(autoNextDelay(done)).toBe(2500);
    expect(reduce(done, { type: 'NEXT' })).toEqual(INITIAL);
  });

  it('bảo vệ chạm "Dừng" trong lúc đếm ngược → không xác nhận', () => {
    const stopped = run([
      { type: 'DETECTED', plate: '29A12345' },
      { type: 'PREVIEWED', verdict: ALLOWED, key: 'k1' },
      { type: 'TICK', elapsedMs: 500 },
      { type: 'STOP' },
      { type: 'TICK', elapsedMs: 5000 },
    ]);
    expect(stopped.kind).toBe('stopped');
  });

  it('bị từ chối → báo đỏ, tự quay lại quét; không bao giờ tới bước xác nhận', () => {
    const denied = run([{ type: 'DETECTED', plate: '29A12345' }, { type: 'PREVIEWED', verdict: DENIED, key: 'k1' }]);
    expect(denied.kind).toBe('denied');
    expect(reduce(denied, { type: 'CONFIRM_NOW' })).toBe(denied);
    expect(autoNextDelay(denied)).toBe(4000);
  });

  it('đang xử lý một xe thì mã QR khác bị bỏ qua', () => {
    const checking = run([{ type: 'DETECTED', plate: '29A12345' }]);
    expect(reduce(checking, { type: 'DETECTED', plate: '30B67890' })).toBe(checking);
  });

  it('lỗi mạng lúc xác nhận: giữ ĐÚNG Idempotency-Key cũ để thử lại, không tự bỏ qua', () => {
    const failed = run([
      { type: 'DETECTED', plate: '29A12345' },
      { type: 'PREVIEWED', verdict: ALLOWED, key: 'k1' },
      { type: 'CONFIRM_NOW' },
      { type: 'FAILED', message: 'Mất mạng' },
    ]);
    expect(failed).toMatchObject({ kind: 'failed', key: 'k1' });
    expect(autoNextDelay(failed)).toBeNull();
    expect(reduce(failed, { type: 'RETRY' })).toEqual({ kind: 'confirming', verdict: ALLOWED, key: 'k1' });
  });

  it('điện thoại còn trước camera: cùng biển số bị bỏ qua trong 10 giây', () => {
    const recent = new RecentPlates();
    recent.mark('29A12345', 1_000);
    expect(recent.isCoolingDown('29A12345', 5_000)).toBe(true);
    expect(recent.isCoolingDown('30B67890', 5_000)).toBe(false);
    expect(recent.isCoolingDown('29A12345', 11_000)).toBe(false);
  });
});
