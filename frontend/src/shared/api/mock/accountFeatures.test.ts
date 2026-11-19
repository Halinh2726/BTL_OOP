import { beforeEach, describe, expect, it } from 'vitest';
import { STANDARD_PLATE, formatPlate } from '../../utils/format';
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

describe('Đợt 1 — biển số theo quy định', () => {
  it('hiển thị có dấu, cùng quy tắc với backend', () => {
    expect(formatPlate('29A1514')).toBe('29A-1514');
    expect(formatPlate('29A12345')).toBe('29A-123.45');
    expect(formatPlate('29A112345')).toBe('29A1-123.45');
    expect(formatPlate('XD00001')).toBe('XD-00001');
    expect(formatPlate('ABC12345')).toBe('ABC12345');
  });

  it('kiểm tra định dạng khi đăng ký mới', () => {
    expect(STANDARD_PLATE.test('29A12345')).toBe(true);
    expect(STANDARD_PLATE.test('XD00042')).toBe(true);
    expect(STANDARD_PLATE.test('29A123')).toBe(false);
    expect(STANDARD_PLATE.test('ABC12345')).toBe(false);
  });
});

describe('Đợt 1 — tài khoản và xe trên dữ liệu giả lập', () => {
  let api: ApiClient;

  beforeEach(() => {
    sessionStorage.clear();
    api = createMockApiClient({ latencyMs: 0, initialState: seedState() });
  });

  it('cập nhật thông tin cá nhân, email trùng thì báo EMAIL_EXISTS', async () => {
    await api.login({ username: 'sv02', password: 'demo1234' });
    expect(await codeOf(api.updateMyProfile({ fullName: 'Trần Thị B', email: 'sv01@ptit.edu.vn', phone: '' }))).toBe('EMAIL_EXISTS');
    const updated = await api.updateMyProfile({ fullName: 'Trần Thị Bình', email: 'B@PTIT.edu.vn', phone: '091 234 5678' });
    expect(updated).toMatchObject({ fullName: 'Trần Thị Bình', email: 'b@ptit.edu.vn', phone: '0912345678', studentCode: 'B21DCCN002' });
  });

  it('quên mật khẩu: nhập sai mã rồi nhập đúng mã thì đăng nhập bằng mật khẩu mới', async () => {
    await api.requestPasswordReset('sv01@ptit.edu.vn');
    expect(await codeOf(api.confirmPasswordReset({ email: 'sv01@ptit.edu.vn', code: '000000', newPassword: 'MatKhauMoi1' })))
      .toBe('RESET_CODE_INVALID');
    await api.confirmPasswordReset({ email: 'sv01@ptit.edu.vn', code: MOCK_OTP, newPassword: 'MatKhauMoi1' });
    expect((await api.login({ username: 'sv01', password: 'MatKhauMoi1' })).user.username).toBe('sv01');
  });

  it('mật khẩu tạm: bắt buộc đổi trước khi dùng chức năng khác', async () => {
    await api.login({ username: 'quanly01', password: 'demo1234' });
    const issued = await api.resetStudentPassword('b21dccn001');
    await api.logout();

    const me = await api.login({ username: 'sv01', password: issued.temporaryPassword });
    expect(me.mustChangePassword).toBe(true);
    expect(await codeOf(api.myVehicles())).toBe('PASSWORD_CHANGE_REQUIRED');
    await api.changePassword({ currentPassword: issued.temporaryPassword, newPassword: 'MatKhauRieng1' });
    expect((await api.myVehicles()).length).toBeGreaterThan(0);
  });

  it('xe: mã XD cho xe đạp, tối đa 3 xe (sv01 đã có 1 xe), ngừng xe đang trong bãi bị chặn, ngừng rồi thì bảo vệ không thấy xe', async () => {
    await api.login({ username: 'sv01', password: 'demo1234' });
    const bike = await api.registerVehicle({ plateNumber: '', vehicleTypeCode: 'XE_DAP' });
    expect(bike.plateDisplay).toMatch(/^XD-\d{5}$/);
    expect(await codeOf(api.registerVehicle({ plateNumber: '', vehicleTypeCode: 'XE_MAY' }))).toBe('PLATE_REQUIRED');
    const car = await api.registerVehicle({ plateNumber: '30K-123.45', vehicleTypeCode: 'XE_MAY' });
    expect(await codeOf(api.registerVehicle({ plateNumber: '30K99999', vehicleTypeCode: 'XE_MAY' }))).toBe('VEHICLE_LIMIT_REACHED');

    await api.logout();
    await api.login({ username: 'baove01', password: 'demo1234' });
    await api.checkIn('30K12345');
    await api.logout();
    await api.login({ username: 'sv01', password: 'demo1234' });
    expect(await codeOf(api.deactivateVehicle(car.id))).toBe('VEHICLE_PARKED');

    const stopped = await api.deactivateVehicle(bike.id);
    expect(stopped.status).toBe('NGUNG_SU_DUNG');
    await api.logout();
    await api.login({ username: 'baove01', password: 'demo1234' });
    expect((await api.previewCheckIn(bike.plateNumber)).reason).toBe('PLATE_NOT_FOUND');
  });
});
