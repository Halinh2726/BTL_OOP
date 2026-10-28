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

describe('mockApiClient (dữ liệu giả lập phải theo đúng hợp đồng API)', () => {
  let api: ApiClient;

  beforeEach(() => {
    sessionStorage.clear();
    api = createMockApiClient({ latencyMs: 0, initialState: seedState() });
  });

  it('đăng nhập đúng/sai, chưa đăng nhập thì 401', async () => {
    expect(await codeOf(api.me())).toBe('UNAUTHENTICATED');
    expect(await codeOf(api.login({ username: 'sv01', password: 'sai' }))).toBe('INVALID_CREDENTIALS');
    const me = await api.login({ username: 'sv01', password: 'MatKhau@123' });
    expect(me.user.role).toBe('SINH_VIEN');
    expect(me.permissions).toContain('WALLET_VIEW_OWN');
    expect((await api.myWallet()).balance).toBe(44000);
  });

  it('sai vai trò -> FORBIDDEN', async () => {
    await api.login({ username: 'sv01', password: 'MatKhau@123' });
    expect(await codeOf(api.guards())).toBe('FORBIDDEN');
  });

  it('xe vào rồi xe ra: trừ điểm sinh viên, cộng cho chủ tài khoản; gửi lại cùng key không trừ lần hai', async () => {
    await api.login({ username: 'baove01', password: 'Baove@123' });
    const preview = await api.previewCheckIn('29a-123.45');
    expect(preview).toMatchObject({ plateNumber: '29A12345', allowed: true, reason: null, minFee: 3000 });

    await api.checkIn('29A12345');
    expect((await api.previewCheckIn('29A12345')).reason).toBe('SESSION_ALREADY_OPEN');
    expect(await codeOf(api.checkIn('29A12345'))).toBe('SESSION_ALREADY_OPEN');

    const out = await api.previewCheckOut('29A12345');
    expect(out.allowed).toBe(true);
    const sessionId = out.sessionId ?? -1;
    const fee = out.fee ?? 0;
    const first = await api.checkOut(sessionId, 'key-1');
    const again = await api.checkOut(sessionId, 'key-1');
    expect(first.replayed).toBe(false);
    expect(again).toEqual({ data: first.data, replayed: true });
    expect(first.data.balanceAfter).toBe(44000 - fee);
    expect(await codeOf(api.checkOut(sessionId, 'key-2'))).toBe('SESSION_ALREADY_PAID');

    await api.logout();
    await api.login({ username: 'chutk01', password: 'Chutk@123' });
    expect((await api.myWallet()).balance).toBe(4000 + fee);
    const history = await api.myTransactions({ type: 'THANH_TOAN' });
    expect(history.items[0]?.change).toBe(fee);
  });

  it('gợi ý biển số: biển kết thúc bằng số vừa gõ xếp trước; xe ra chỉ gợi ý xe trong bãi', async () => {
    await api.login({ username: 'baove01', password: 'Baove@123' });
    expect((await api.suggestPlates('45', false)).map((s) => s.plateNumber)).toEqual(['29A12345']);
    expect((await api.suggestPlates('0', false).catch((e: unknown) => e)) instanceof ApiError).toBe(true);
    expect(await api.suggestPlates('12345', true)).toEqual([]);
    await api.checkIn('29A12345');
    expect(await api.suggestPlates('2345', true)).toEqual([{ plateNumber: '29A12345', vehicleTypeCode: 'XE_MAY', parked: true }]);
  });

  it('không đủ điểm thì không cho xe vào', async () => {
    await api.login({ username: 'baove01', password: 'Baove@123' });
    expect(await api.previewCheckIn('30B67890')).toMatchObject({ allowed: false, reason: 'INSUFFICIENT_POINTS' });
    expect(await codeOf(api.checkIn('30B67890'))).toBe('INSUFFICIENT_POINTS');
    expect(await api.previewCheckIn('99Z99999')).toMatchObject({ allowed: false, reason: 'PLATE_NOT_FOUND', fullName: null });
  });

  it('đổi điểm, rút điểm; cùng key khác nội dung -> IDEMPOTENCY_KEY_REUSED', async () => {
    await api.login({ username: 'quanly01', password: 'Quanly@123' });
    const deposit = await api.deposit({ studentCode: 'b21dccn001', cashAmount: 50000 }, 'd-1');
    expect(deposit.data).toMatchObject({ studentCode: 'B21DCCN001', points: 50000, balanceBefore: 44000, balanceAfter: 94000 });
    expect(await codeOf(api.deposit({ studentCode: 'B21DCCN001', cashAmount: 10000 }, 'd-1'))).toBe('IDEMPOTENCY_KEY_REUSED');
    expect(await codeOf(api.deposit({ studentCode: 'B21DCCN001', cashAmount: 0 }, 'd-2'))).toBe('INVALID_AMOUNT');
    expect(await codeOf(api.lookupStudent('KHONGCO'))).toBe('STUDENT_NOT_FOUND');

    expect(await codeOf(api.withdraw(999999, 'w-1'))).toBe('INSUFFICIENT_POINTS');
    const withdrawal = await api.withdraw(1000, 'w-2');
    expect(withdrawal.data).toMatchObject({ cashAmount: 1000, balanceBefore: 4000, balanceAfter: 3000 });
  });

  it('khóa bảo vệ -> bảo vệ đó không đăng nhập được; tên đăng nhập trùng -> USERNAME_EXISTS', async () => {
    await api.login({ username: 'quanly01', password: 'Quanly@123' });
    const guards = await api.guards();
    const guard = guards.find((candidate) => candidate.username === 'baove01');
    await api.changeGuardStatus(guard?.id ?? -1, 'BI_KHOA');
    expect(await codeOf(api.createGuard({ fullName: 'X', phone: '0911111111', username: 'baove01', password: '12345678' }))).toBe(
      'USERNAME_EXISTS',
    );
    await api.logout();
    expect(await codeOf(api.login({ username: 'baove01', password: 'Baove@123' }))).toBe('ACCOUNT_LOCKED');
  });

  it('lịch sử phân trang bằng before/nextBefore', async () => {
    await api.login({ username: 'sv01', password: 'MatKhau@123' });
    const first = await api.myTransactions({ limit: 2 });
    expect(first.items.map((item) => item.id)).toEqual([4, 3]);
    expect(first).toMatchObject({ hasMore: true, nextBefore: 3 });
    const second = await api.myTransactions({ limit: 2, before: first.nextBefore ?? undefined });
    expect(second.items.map((item) => item.id)).toEqual([1]);
    expect(second.hasMore).toBe(false);
  });

  it('đăng ký sinh viên: kiểm tra dữ liệu và trùng MSSV', async () => {
    const failure = await api
      .registerStudent({ studentCode: 'b21-01', fullName: 'X', username: 'AB', password: '123' })
      .catch((error: unknown) => error as ApiError);
    expect((failure as ApiError).fields.map((field) => field.field)).toEqual(['studentCode', 'username', 'password']);
    expect(await codeOf(api.registerStudent({ studentCode: 'B21DCCN001', fullName: 'X', username: 'moi01', password: '12345678' }))).toBe(
      'STUDENT_CODE_EXISTS',
    );
  });
});
