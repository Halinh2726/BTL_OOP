import { beforeEach, describe, expect, it } from 'vitest';
import type { ApiClient } from '../ApiClient';
import { ApiError } from '../ApiError';
import { createMockApiClient } from './mockApiClient';
import { seedState } from './mockState';

async function codeOf(promise: Promise<unknown>): Promise<string> {
  const failure = await promise.then(
    () => null,
    (error: unknown) => error,
  );
  return failure instanceof ApiError ? failure.code : 'NO_ERROR';
}

describe('Đợt 3 — rút điểm theo yêu cầu, điều chỉnh, tài chính chủ bãi xe (mock)', () => {
  let api: ApiClient;

  beforeEach(() => {
    sessionStorage.clear();
    api = createMockApiClient({ latencyMs: 0, initialState: seedState() });
  });

  it('chủ bãi tạo yêu cầu (R15), quản lý trả tiền -> trừ điểm; bấm lại không trừ lần hai; chủ bãi xác nhận', async () => {
    await api.login({ username: 'chutk01', password: 'demo1234' });
    expect((await api.ownerOverview()).balance).toBe(4000);
    expect(await codeOf(api.createWithdrawalRequest(5000))).toBe('WITHDRAWAL_EXCEEDS_AVAILABLE');
    const request = await api.createWithdrawalRequest(3000);
    expect(await codeOf(api.createWithdrawalRequest(2000))).toBe('WITHDRAWAL_EXCEEDS_AVAILABLE');   // 3.000 đang chờ
    expect((await api.ownerOverview()).balance).toBe(4000);                                          // chưa trừ
    await api.logout();

    await api.login({ username: 'quanly01', password: 'demo1234' });
    const queue = await api.withdrawalQueue('CHO_XU_LY');
    expect(queue.items.map((item) => item.id)).toEqual([request.id]);
    expect((await api.payWithdrawalRequest(request.id)).replayed).toBe(false);
    expect((await api.payWithdrawalRequest(request.id)).replayed).toBe(true);
    await api.logout();

    await api.login({ username: 'chutk01', password: 'demo1234' });
    expect((await api.ownerOverview()).balance).toBe(1000);
    expect((await api.confirmWithdrawalRequest(request.id)).status).toBe('DA_XAC_NHAN');
  });

  it('giao dịch điều chỉnh: bắt buộc lý do, chuyển điểm chủ bãi -> sinh viên, hiện trong lịch sử sinh viên', async () => {
    await api.login({ username: 'quanly01', password: 'demo1234' });
    expect(await codeOf(api.adjustPoints({ studentCode: 'b21dccn001', direction: 'HOAN_DIEM', points: 1000, reason: ' ' }, 'a-1')))
      .toBe('VALIDATION_ERROR');
    const result = await api.adjustPoints(
      { studentCode: 'b21dccn001', direction: 'HOAN_DIEM', points: 1000, reason: 'Trừ phí hai lần', originalTransactionId: 4 },
      'a-2',
    );
    expect(result.data).toMatchObject({ studentName: 'Nguyễn Văn A', points: 1000 });
    await api.logout();

    await api.login({ username: 'sv01', password: 'demo1234' });
    const history = await api.myTransactions({});
    expect(history.items[0]).toMatchObject({ type: 'DIEU_CHINH', change: 1000 });
  });

  it('doanh thu theo ngày: tổng phí, tiền mặt vào / ra', async () => {
    await api.login({ username: 'chutk01', password: 'demo1234' });
    const report = await api.ownerRevenue('2000-01-01', '2100-12-31');
    expect(report.totalRevenue).toBe(6000);
    expect(report.totalCashIn).toBe(52000);
    expect(report.totalCashOut).toBe(2000);
  });
});
