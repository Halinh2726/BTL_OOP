import { beforeEach, describe, expect, it } from 'vitest';
import { formatDuration } from '../../utils/format';
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

describe('Đợt 2 — màn hình bảo vệ trên dữ liệu giả lập', () => {
  let api: ApiClient;

  beforeEach(() => {
    sessionStorage.clear();
    api = createMockApiClient({ latencyMs: 0, initialState: seedState() });
  });

  it('thời gian gửi dễ đọc', () => {
    expect(formatDuration(1500)).toBe('1 ngày 1 giờ');
    expect(formatDuration(95)).toBe('1 giờ 35 phút');
    expect(formatDuration(7)).toBe('7 phút');
  });

  it('xe trong bãi: đếm xe, đánh dấu gửi quá 24 giờ, lọc theo biển số, không có trường điểm', async () => {
    await api.login({ username: 'baove01', password: 'demo1234' });
    const lot = await api.parkedVehicles();
    expect(lot.total).toBe(1);
    expect(lot.overdue).toBe(1);
    expect(lot.items[0]).toMatchObject({ plateDisplay: '29T-670.21', overdue: true });
    expect(lot.items[0]).not.toHaveProperty('balance');
    expect((await api.parkedVehicles('999')).items).toHaveLength(0);
  });

  it('nhận ca → cho xe vào → kết thúc ca có số liệu → bảo vệ khác phải xác nhận biên bản mới nhận ca', async () => {
    await api.login({ username: 'baove01', password: 'demo1234' });
    await api.startShift([], 200000);
    await api.checkIn('29A12345');
    expect((await api.myGateActivity()).checkIns).toBe(1);
    const ended = await api.endShift('Đèn cổng sau hỏng', 350000);
    expect(ended.summary).toMatchObject({ checkIns: 1, vehiclesInLot: 2 });
    await api.logout();

    await api.login({ username: 'quanly01', password: 'demo1234' });
    await api.createGuard({ fullName: 'Ngô Văn H', phone: '0912345678', username: 'baove02', password: 'Baove@456' });
    await api.logout();
    await api.login({ username: 'baove02', password: 'Baove@456' });
    const overview = await api.currentShift();
    expect(overview.pendingHandovers.map((shift) => shift.id)).toEqual([ended.id]);
    expect(await codeOf(api.startShift([], 0))).toBe('HANDOVER_PENDING');
    const mine = await api.startShift([ended.id], 300000);
    expect(mine).toMatchObject({ expectedOpeningCash: 350000, openingDiscrepancy: -50000 });
    expect((await api.currentShift()).pendingHandovers).toHaveLength(0);

    // Chấm công: bảo vệ xem lịch sử ca của mình; quản lý xem bảng tổng hợp
    expect((await api.myShiftHistory())[0]).toMatchObject({ openingCash: 300000, closingCash: null });
    await api.logout();
    await api.login({ username: 'quanly01', password: 'demo1234' });
    const today = new Date(Date.now() + 7 * 3600_000).toISOString().slice(0, 10);
    const sheet = await api.guardTimesheet(today, today);
    expect(sheet.shifts).toHaveLength(2);
    expect(sheet.totals.find((total) => total.guardName === 'Ngô Văn H')?.cashMismatches).toBe(1);
  });

  it('báo sự cố kèm ảnh, sự cố chưa xử lý hiện trong màn hình ca trực', async () => {
    await api.login({ username: 'baove01', password: 'demo1234' });
    expect(await codeOf(api.reportIncident({ type: 'KHAC', plateNumber: '', description: '  ' }))).toBe('VALIDATION_ERROR');
    const incident = await api.reportIncident({
      type: 'XE_HU_HONG', plateNumber: '30b-678.90', description: 'Vỡ gương',
      photoContentType: 'image/jpeg', photoBase64: 'data:image/jpeg;base64,/9j/AA==',
    });
    expect(incident).toMatchObject({ status: 'MOI', hasPhoto: true, plateDisplay: '30B-678.90' });
    expect(api.incidentPhotoUrl(incident.id)).toMatch(/^data:image\/jpeg/);
    expect((await api.currentShift()).openIncidents.map((item) => item.id)).toContain(incident.id);
  });
});
