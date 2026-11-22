import { beforeEach, describe, expect, it } from 'vitest';
import type { ApiClient } from '../ApiClient';
import { ApiError } from '../ApiError';
import { createMockApiClient } from './mockApiClient';
import { MOCK_OTP, seedState } from './mockState';

async function codeOf(promise: Promise<unknown>): Promise<string> {
  const failure = await promise.then(
    () => null,
    (error: unknown) => error,
  );
  return failure instanceof ApiError ? failure.code : 'NO_ERROR';
}

describe('Tối ưu sinh viên & xác thực (mock)', () => {
  let api: ApiClient;

  beforeEach(() => {
    sessionStorage.clear();
    api = createMockApiClient({ latencyMs: 0, initialState: seedState() });
  });

  async function as(username: string) {
    await api.logout().catch(() => undefined);
    await api.login({ username, password: 'demo1234' });
  }

  it('đăng ký kèm email khôi phục, quên mật khẩu bằng MSSV/tên đăng nhập và xem bảo mật cá nhân', async () => {
    await api.registerStudent({
      studentCode: 'B21DCCN099',
      fullName: 'Lê Hoàng C',
      username: 'sv099',
      password: 'MatKhau@123',
      email: 'SV099@PTIT.edu.vn',
    });
    expect(await codeOf(api.registerStudent({
      studentCode: 'B21DCCN100',
      fullName: 'Người trùng email',
      username: 'sv100',
      password: 'MatKhau@123',
      email: 'sv099@ptit.edu.vn',
    }))).toBe('EMAIL_EXISTS');

    await api.requestPasswordReset('b21dccn099');
    await api.confirmPasswordReset({ identifier: 'sv099', code: MOCK_OTP, newPassword: 'MatKhauMoi@9' });
    await api.login({ username: 'sv099', password: 'MatKhauMoi@9' });

    const security = await api.mySecurityActivity();
    expect(security.hasEmail).toBe(true);
    expect(security.maskedEmail).toBe('s***@ptit.edu.vn');
    expect(security.recentActivities.some((a) => a.action === 'KHOI_PHUC_MAT_KHAU')).toBe(true);
    expect((await api.notifications()).items[0]?.kind).toBe('BAO_MAT');
  });

  it('mua vé 3 tháng liên tiếp theo nguyên giá (không giảm giá) và quản lý cập nhật giá vé', async () => {
    await as('quanly01');
    const catalog = await api.managerPassCatalog();
    expect(catalog.prices.length).toBeGreaterThan(0);
    await api.deposit({ studentCode: 'B21DCCN001', cashAmount: 300_000 }, 'dep-multi');

    await as('sv01');
    const overview = await api.myPasses();
    const vehicle = overview.options[0]!;
    const bought = await api.buyPass(
      { vehicleId: vehicle.vehicleId, month: overview.currentMonth, monthsCount: 3 },
      'buy-3-months',
    );
    expect(bought.data.monthsCount).toBe(3);
    expect(bought.data.totalPrice).toBe(300_000);
    expect((await api.myPasses()).options[0]?.remainingDays).toBeGreaterThan(25);

    await as('quanly01');
    const updated = await api.updatePassPrice('XE_MAY', 110_000);
    expect(updated.prices.find((p) => p.vehicleTypeCode === 'XE_MAY')?.monthlyPoints).toBe(110_000);
  });

  it('thông báo xe vào, xe ra trừ điểm và cảnh báo sớm khi còn dưới 2 lượt tối thiểu', async () => {
    await as('sv01');
    const v = (await api.myVehicles())[0]!;

    await as('baove01');
    const entered = await api.checkIn(v.plateNumber);
    await as('sv01');
    expect((await api.notifications()).items[0]?.kind).toBe('XE_VAO');

    await as('quanly01');
    await api.deposit({ studentCode: 'B21DCCN002', cashAmount: 6_000 }, 'dep-sv02'); // sv02 có 2.000 -> 8.000
    await as('baove01');
    const sv02Session = (await api.previewCheckOut('29T67021')).sessionId ?? 0;
    const res = await api.checkOut(sv02Session, 'out-sv02-early');
    // nếu ban ngày 3.000 -> còn 5.000 (< 6.000), nếu ban đêm 5.000 -> còn 3.000 (< 6.000)
    expect(res.data.balanceAfter).toBeLessThan(6_000);

    await as('sv02');
    const inbox = await api.notifications();
    expect(inbox.items.slice(0, 2).map((n) => n.kind)).toEqual(['SO_DU_THAP', 'XE_RA']);

    await as('baove01');
    await api.checkOut(entered.sessionId, 'out-sv01');
  });

  it('lịch sử gửi xe sinh viên và yêu cầu trích xuất camera / cập nhật từ quản lý', async () => {
    await as('sv01');
    const sessions = await api.mySessions();
    expect(sessions.recent.length).toBeGreaterThan(0);

    const req = await api.submitComplaint({
      type: 'TRICH_XUAT_CAMERA',
      sessionId: sessions.recent[0]!.id,
      description: 'Xin xem lại camera cổng chính',
      incidentArea: 'Cổng chính A1',
      incidentTimeRange: '16:30–17:00',
    });
    expect(req.cameraRequested).toBe(true);
    expect(req.cameraStatus).toBe('CHO_TRICH_XUAT');

    await as('quanly01');
    const reviewed = await api.updateComplaintCamera(req.id, {
      cameraStatus: 'DA_TRICH_XUAT',
      cameraNote: 'Đã trích xuất góc cổng chính',
      photoContentType: 'image/png',
      photoBase64: 'data:image/png;base64,iVBORw0KGgo=',
    });
    expect(reviewed.cameraStatus).toBe('DA_TRICH_XUAT');
    expect(reviewed.hasCameraPhoto).toBe(true);

    await as('sv01');
    expect((await api.notifications()).items[0]?.kind).toBe('CAMERA');
  });
});
