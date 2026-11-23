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

describe('Đợt 5 — vé tháng & thông báo (mock)', () => {
  let api: ApiClient;

  beforeEach(() => {
    sessionStorage.clear();
    api = createMockApiClient({ latencyMs: 0, initialState: seedState() });
  });

  async function as(username: string) {
    await api.logout().catch(() => undefined);
    await api.login({ username, password: 'demo1234' });
  }

  it('mua vé tháng: tháng này / tháng sau, gửi lặp cùng key không trừ lần hai, khác key thì báo đã có vé', async () => {
    await as('sv01');
    const overview = await api.myPasses();
    const option = overview.options[0];
    expect(option).toMatchObject({ plateNumber: '29A12345', price: 100_000, current: null, next: null });
    expect(await codeOf(api.buyPass({ vehicleId: option?.vehicleId ?? 0, month: '2000-01' }, 'k0'))).toBe('PASS_MONTH_INVALID');
    expect(await codeOf(api.buyPass({ vehicleId: option?.vehicleId ?? 0, month: overview.currentMonth }, 'k1')))
      .toBe('INSUFFICIENT_POINTS');                                                               // sv01 có 44.000

    await as('quanly01');
    await api.deposit({ studentCode: 'B21DCCN001', cashAmount: 60_000 }, 'dep');
    await as('sv01');
    const first = await api.buyPass({ vehicleId: option?.vehicleId ?? 0, month: overview.currentMonth }, 'k2');
    expect(first.replayed).toBe(false);
    expect(first.data.balanceAfter).toBe(4_000);
    expect((await api.buyPass({ vehicleId: option?.vehicleId ?? 0, month: overview.currentMonth }, 'k2')).replayed).toBe(true);
    expect(await codeOf(api.buyPass({ vehicleId: option?.vehicleId ?? 0, month: overview.currentMonth }, 'k3')))
      .toBe('PASS_ALREADY_EXISTS');
    expect((await api.myWallet()).balance).toBe(4_000);
    expect((await api.myPasses()).options[0]?.current?.validUntil.startsWith(overview.currentMonth)).toBe(true);
    expect((await api.myTransactions({})).items[0]).toMatchObject({ type: 'MUA_VE', change: -100_000 });
    expect((await api.notifications()).items[0]).toMatchObject({ kind: 'VE_THANG', read: false });
  });

  it('xe có vé: vào không cần điểm, ra miễn phí không có giao dịch; xe không vé trả phí và bị nhắc số dư thấp', async () => {
    await as('quanly01');
    await api.deposit({ studentCode: 'B21DCCN001', cashAmount: 60_000 }, 'dep');
    await as('sv01');
    const overview = await api.myPasses();
    await api.buyPass({ vehicleId: overview.options[0]?.vehicleId ?? 0, month: overview.currentMonth }, 'k');

    await as('baove01');
    expect(await api.previewCheckIn('29A12345')).toMatchObject({ minFee: 0, allowed: true });
    await api.checkIn('29A12345');
    const preview = await api.previewCheckOut('29A12345');
    expect(preview).toMatchObject({ fee: 0, allowed: true });
    expect(preview.passValidUntil).toBeTruthy();
    const exit = await api.checkOut(preview.sessionId ?? 0, 'exit-1');
    expect(exit.data).toMatchObject({ transactionId: null, fee: 0, balanceAfter: 4_000 });

    // sv02 gửi xe 30 giờ: phí 12.000 điểm. Nạp thêm để sau khi trả phí còn 1.000 điểm.
    expect(await api.previewCheckOut('29T67021')).toMatchObject({
      fee: 12_000, allowed: false, reason: 'INSUFFICIENT_POINTS',
    });
    await as('quanly01');
    await api.deposit({ studentCode: 'B21DCCN002', cashAmount: 11_000 }, 'dep-2');
    await as('baove01');
    const lowPreview = await api.previewCheckOut('29T67021');
    expect(lowPreview.passValidUntil).toBeNull();
    expect((await api.checkOut(lowPreview.sessionId ?? 0, 'exit-2')).data.balanceAfter).toBe(1_000);
    await as('sv02');
    expect((await api.notifications()).items[0]).toMatchObject({ kind: 'SO_DU_THAP' });
  });

  it('thông báo: khiếu nại mới báo quản lý; đánh dấu một / tất cả đã đọc; không đọc được thông báo của người khác', async () => {
    await as('sv01');
    await api.submitComplaint({ type: 'KHAC', description: 'Góp ý' });
    const mine = await api.notifications();
    expect(mine.unread).toBe(0);

    await as('quanly01');
    const inbox = await api.notifications();
    expect(inbox.unread).toBe(1);
    expect(inbox.items[0]?.kind).toBe('KHIEU_NAI');
    const after = await api.markNotificationRead(inbox.items[0]?.id ?? 0);
    expect(after.unread).toBe(0);
    expect(after.items[0]?.read).toBe(true);

    await as('baove01');
    await api.reportIncident({ type: 'KHAC', plateNumber: '', description: 'x' });
    expect((await api.markAllNotificationsRead()).unread).toBe(0);
  });
});
