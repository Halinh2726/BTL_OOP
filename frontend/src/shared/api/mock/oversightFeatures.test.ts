import { beforeEach, describe, expect, it } from 'vitest';
import type { ApiClient } from '../ApiClient';
import { ApiError } from '../ApiError';
import { createMockApiClient } from './mockApiClient';
import { seedState, toVietnamIso } from './mockState';

async function codeOf(promise: Promise<unknown>): Promise<string> {
  const failure = await promise.then(
    () => null,
    (error: unknown) => error,
  );
  return failure instanceof ApiError ? failure.code : 'NO_ERROR';
}

const today = () => toVietnamIso(new Date()).slice(0, 10);

describe('Đợt 4a — giám sát & đối soát của quản lý (mock)', () => {
  let api: ApiClient;

  beforeEach(async () => {
    sessionStorage.clear();
    api = createMockApiClient({ latencyMs: 0, initialState: seedState() });
    await api.login({ username: 'quanly01', password: 'demo1234' });
  });

  it('tìm sinh viên: không dấu vẫn tìm được, ít nhất 2 ký tự; hồ sơ có xe, lượt gửi, giao dịch', async () => {
    expect(await codeOf(api.searchStudents('a'))).toBe('VALIDATION_ERROR');
    const rows = await api.searchStudents('nguyen van');
    expect(rows.map((row) => row.studentCode)).toEqual(['B21DCCN001']);

    const profile = await api.studentProfile('b21dccn001');
    expect(profile.student.balance).toBe(44000);
    expect(profile.vehicles[0]?.plateNumber).toBe('29A12345');
    expect(profile.recentSessions).toHaveLength(2);
    expect(profile.recentTransactions.map((t) => t.type)).toEqual(['THANH_TOAN', 'THANH_TOAN', 'DOI_DIEM']);
    expect(await codeOf(api.studentProfile('KHONGCO'))).toBe('STUDENT_NOT_FOUND');
  });

  it('sổ giao dịch: mới nhất trước, lọc theo MSSV / loại, phân trang; bên chuyển / nhận / người thực hiện', async () => {
    const all = await api.ledger({});
    expect(all.items.map((t) => t.id)).toEqual([5, 4, 3, 2, 1]);
    const deposit = all.items.find((t) => t.type === 'DOI_DIEM' && t.to?.studentCode === 'B21DCCN001');
    expect(deposit).toMatchObject({ from: null, cashAmount: 50000, performerRole: 'QUAN_LY' });
    const payment = all.items.find((t) => t.type === 'THANH_TOAN');
    expect(payment).toMatchObject({ from: { studentCode: 'B21DCCN001' }, to: { role: 'CHU_TAI_KHOAN' }, performerRole: 'BAO_VE' });

    expect((await api.ledger({ studentCode: 'b21dccn002' })).items.map((t) => t.id)).toEqual([2]);
    expect((await api.ledger({ type: 'RUT_DIEM' })).items.map((t) => t.id)).toEqual([5]);

    const first = await api.ledger({ limit: 2 });
    expect(first).toMatchObject({ hasMore: true, nextBefore: 4 });
    const second = await api.ledger({ limit: 2, before: first.nextBefore ?? undefined });
    expect(second.items.map((t) => t.id)).toEqual([3, 2]);

    expect(await codeOf(api.ledger({ from: '2026-11-20', to: '2026-11-19' }))).toBe('DATE_RANGE_INVALID');
  });

  it('điều chỉnh có lý do -> sổ giao dịch hiện lý do và giao dịch gốc', async () => {
    await api.adjustPoints({ studentCode: 'B21DCCN001', direction: 'HOAN_DIEM', points: 1000, reason: 'Trừ phí hai lần', originalTransactionId: 4 }, 'k-1');
    const [latest] = (await api.ledger({ type: 'DIEU_CHINH' })).items;
    expect(latest).toMatchObject({ reason: 'Trừ phí hai lần', originalTransactionId: 4, to: { studentCode: 'B21DCCN001' } });
  });

  it('xuất CSV: cần khoảng ngày (tối đa 93 ngày), có BOM và dòng tiêu đề', async () => {
    expect(await codeOf(api.exportLedger({ from: '2026-01-01', to: '2026-12-31' }))).toBe('DATE_RANGE_INVALID');
    const blob = await api.exportLedger({ from: '2000-01-01', to: '2000-02-01' });
    // jsdom chưa có Blob.text(): đọc bằng FileReader như trình duyệt cũ
    const text = await new Promise<string>((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.readAsText(blob);
    });
    expect(text.split('\r\n')[0]).toContain('Mã GD,Thời gian,Loại');
  });

  it('đối soát: phải có = đầu ngày + vào − ra; lệch phải giải trình; chốt rồi không chốt lại; không chốt ngày mai', async () => {
    await api.deposit({ studentCode: 'B21DCCN002', cashAmount: 10000 }, 'd-1');
    const day = await api.reconciliationDay(today());
    expect(day.cashIn).toBe(10000);
    expect(day.expectedCash).toBe(day.openingCash + 10000 - day.cashOut);
    expect(day.pointsBalanced).toBe(true);
    expect(day.closed).toBeNull();

    expect(await codeOf(api.closeReconciliation({ date: today(), countedCash: day.expectedCash - 1000 })))
      .toBe('RECONCILIATION_NOTE_REQUIRED');
    const closed = await api.closeReconciliation({ date: today(), countedCash: day.expectedCash - 1000, note: 'Trả nhầm tiền thừa' });
    expect(closed).toMatchObject({ outcome: 'THIEU', discrepancy: -1000, closedByName: 'Phạm Văn Quản' });
    expect(await codeOf(api.closeReconciliation({ date: today(), countedCash: day.expectedCash }))).toBe('ALREADY_RECONCILED');
    expect((await api.reconciliationDay(today())).closed?.outcome).toBe('THIEU');
    expect(await api.reconciliationHistory()).toHaveLength(1);

    const tomorrow = toVietnamIso(new Date(Date.now() + 86_400_000)).slice(0, 10);
    expect(await codeOf(api.reconciliationDay(tomorrow))).toBe('RECONCILIATION_DATE_INVALID');
  });

  it('thống kê lượt xe: 24 giờ, chủ bãi xem được, sinh viên thì không; số ngày chỉ 7 / 30 / 90', async () => {
    const report = await api.trafficStats(30);
    expect(report.hours).toHaveLength(24);
    expect(report.totalEntries).toBe(3);
    expect(report.parkedNow).toBe(1);
    expect(await codeOf(api.trafficStats(14))).toBe('VALIDATION_ERROR');
    await api.logout();

    await api.login({ username: 'chutk01', password: 'demo1234' });
    expect((await api.trafficStats(7)).hours).toHaveLength(24);
    await api.logout();

    await api.login({ username: 'sv01', password: 'demo1234' });
    expect(await codeOf(api.trafficStats(7))).toBe('FORBIDDEN');
    expect(await codeOf(api.ledger({}))).toBe('FORBIDDEN');
  });
});
