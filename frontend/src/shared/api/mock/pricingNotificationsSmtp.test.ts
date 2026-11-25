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

describe('Bảng giá theo loại xe, thông báo chuông sự cố & SMTP (mock)', () => {
  let api: ApiClient;

  beforeEach(() => {
    sessionStorage.clear();
    api = createMockApiClient({ latencyMs: 0, initialState: seedState() });
  });

  async function as(username: string) {
    await api.logout().catch(() => undefined);
    await api.login({ username, password: 'demo1234' });
  }

  it('quản lý sửa bảng giá lượt theo loại xe và bảo vệ áp dụng ngay mức giá mới', async () => {
    await as('quanly01');
    const catalog = await api.managerFeeCatalog();
    expect(catalog.schedules.map((s) => s.vehicleTypeCode)).toEqual(['XE_MAY', 'XE_DAP', 'XE_MAY_DIEN']);

    const updated = await api.updateFeeRules('XE_MAY_DIEN', [
      { upToMinutes: 1080, feePoints: 2000 },
      { upToMinutes: 1320, feePoints: 3500 },
      { upToMinutes: 1440, feePoints: 12000 },
    ]);
    expect(updated.schedules.find((s) => s.vehicleTypeCode === 'XE_MAY_DIEN')?.rules[0]?.feePoints).toBe(2000);

    // Từ chối bảng giá thiếu khung cố định
    expect(await codeOf(api.updateFeeRules('XE_DAP', [{ upToMinutes: 1080, feePoints: 1000 }]))).toBe('VALIDATION_ERROR');

    // Sinh viên đăng ký xe đạp và vào/ra bãi với mức phí riêng của xe đạp
    await as('sv01');
    const bike = await api.registerVehicle({ plateNumber: '', vehicleTypeCode: 'XE_DAP' });
    const publicSchedules = await api.feeRulesByVehicleType();
    expect(publicSchedules.find((s) => s.vehicleTypeCode === 'XE_MAY_DIEN')?.rules[0]?.feePoints).toBe(2000);

    await as('quanly01');
    await api.updateFeeRules('XE_DAP', [
      { upToMinutes: 1080, feePoints: 1000 },
      { upToMinutes: 1320, feePoints: 2000 },
      { upToMinutes: 1440, feePoints: 10000 },
    ]);

    await as('baove01');
    const checkInPreview = await api.previewCheckIn(bike.plateNumber);
    expect(checkInPreview.minFee).toBe(1000);
    expect(checkInPreview.allowed).toBe(true);
    const entered = await api.checkIn(bike.plateNumber);
    const checkOutPreview = await api.previewCheckOut(bike.plateNumber);
    expect(checkOutPreview.fee).toBeGreaterThanOrEqual(1000);
    const exited = await api.checkOut(entered.sessionId, 'out-bike-custom');
    expect(exited.data.fee).toBe(checkOutPreview.fee);
  });

  it('báo cáo sự cố tạo thông báo chuông cho quản lý, chủ xe và bảo vệ', async () => {
    await as('baove01');
    const incident = await api.reportIncident({
      type: 'XE_HU_HONG',
      plateNumber: '29A12345',
      description: 'Vỡ đèn xi nhan trái',
    });

    await as('quanly01');
    const managerInbox = await api.notifications();
    expect(managerInbox.unread).toBe(1);
    expect(managerInbox.items[0]).toMatchObject({ kind: 'SU_CO', read: false });
    expect(managerInbox.items[0]?.title).toContain(`#${incident.id}`);

    await api.startIncident(incident.id);
    await api.resolveIncident(incident.id, 'Đã liên hệ chủ xe');

    await as('sv01');
    const studentInbox = await api.notifications();
    expect(studentInbox.items.some((n) => n.kind === 'SU_CO' && n.title.includes(`#${incident.id}`))).toBe(true);

    await as('baove01');
    const guardInbox = await api.notifications();
    expect(guardInbox.items[0]).toMatchObject({ kind: 'SU_CO' });
    expect(guardInbox.items[0]?.body).toContain('Đã liên hệ chủ xe');
  });

  it('quản lý cấu hình SMTP, giữ mật khẩu khi cập nhật lại và gửi email thử', async () => {
    await as('quanly01');
    const initial = await api.smtpConfig();
    expect(initial.active).toBe(false);
    expect(initial.hasPassword).toBe(false);

    const saved = await api.updateSmtpConfig({
      enabled: true,
      host: 'smtp.gmail.com',
      port: 587,
      username: 'parking@gmail.com',
      password: 'abcd efgh ijkl mnop',
      starttls: true,
      sslEnabled: false,
      alertEmail: 'alert@ptit.edu.vn',
    });
    expect(saved.active).toBe(true);
    expect(saved.hasPassword).toBe(true);
    expect(saved.source).toBe('DATABASE');
    expect(saved.fromAddress).toBe('parking@gmail.com');

    // Gửi thử về alertEmail mặc định
    const tested = await api.testSmtpConfig();
    expect(tested.toEmail).toBe('alert@ptit.edu.vn');

    const reset = await api.resetSmtpConfig();
    expect(reset.source).toBe('ENV');
    expect(reset.active).toBe(false);
  });
});
