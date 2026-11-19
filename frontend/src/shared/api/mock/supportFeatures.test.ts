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

const dayAfter = (days: number) => toVietnamIso(new Date(Date.now() + days * 86_400_000)).slice(0, 10);

describe('Đợt 4b — khiếu nại, bồi thường, sự cố, phân ca (mock)', () => {
  let api: ApiClient;

  beforeEach(() => {
    sessionStorage.clear();
    api = createMockApiClient({ latencyMs: 0, initialState: seedState() });
  });

  async function as(username: string) {
    await api.logout().catch(() => undefined);
    await api.login({ username, password: 'demo1234' });
  }

  it('thu phí sai: gắn lượt gửi của mình, quản lý hoàn phí tối đa bằng phí đã thu', async () => {
    await as('sv01');
    const sessions = await api.complaintSessions();
    const paid = sessions.find((s) => s.fee !== null);
    expect(paid?.fee).toBe(3000);
    expect(await codeOf(api.submitComplaint({ type: 'THU_PHI_SAI', sessionId: 3, description: 'Lượt của bạn khác' })))
      .toBe('PARKING_SESSION_NOT_FOUND');
    const complaint = await api.submitComplaint({ type: 'THU_PHI_SAI', sessionId: paid?.id, description: 'Trừ nhầm' });
    expect(complaint.status).toBe('MOI');

    await as('quanly01');
    expect((await api.managerComplaints()).map((c) => c.id)).toEqual([complaint.id]);
    expect(await codeOf(api.resolveComplaint(complaint.id, 'Hoàn', 3000))).toBe('COMPLAINT_STATUS_CONFLICT');   // chưa tiếp nhận
    await api.startComplaint(complaint.id);
    expect(await codeOf(api.resolveComplaint(complaint.id, 'Hoàn', 5000))).toBe('REFUND_EXCEEDS_FEE');
    const resolved = await api.resolveComplaint(complaint.id, 'Đúng là trừ nhầm', 3000);
    expect(resolved).toMatchObject({ status: 'DA_XU_LY', resolution: 'Đúng là trừ nhầm' });
    expect(resolved.refundTransactionId).not.toBeNull();

    await as('sv01');
    expect((await api.myWallet()).balance).toBe(47000);
    const [latest] = (await api.myTransactions({})).items;
    expect(latest).toMatchObject({ type: 'DIEU_CHINH', change: 3000 });
  });

  it('bồi thường: chỉ hư hỏng / mất đồ, chủ bãi duyệt (thiếu điểm thì không đổi gì), duyệt lại không trả lần hai', async () => {
    await as('sv01');
    const damage = await api.submitComplaint({ type: 'XE_HU_HONG', description: 'Vỡ gương' });
    const fee = await api.submitComplaint({ type: 'THU_PHI_SAI', description: 'Không gắn lượt' });

    await as('quanly01');
    await api.startComplaint(fee.id);
    expect(await codeOf(api.proposeCompensation(fee.id, 1000, 'x'))).toBe('COMPLAINT_NOT_COMPENSABLE');
    expect(await codeOf(api.resolveComplaint(fee.id, 'Hoàn', 1000))).toBe('REFUND_NOT_ALLOWED');
    await api.startComplaint(damage.id);
    await api.proposeCompensation(damage.id, 9000, 'Camera ghi lại');

    await as('chutk01');
    expect((await api.ownerCompensations())[0]).toMatchObject({ id: damage.id, status: 'CHO_DUYET_BOI_THUONG', proposedPoints: 9000 });
    expect(await codeOf(api.approveCompensation(damage.id, ''))).toBe('INSUFFICIENT_POINTS');            // chủ bãi có 4.000
    expect((await api.ownerCompensations())[0]?.status).toBe('CHO_DUYET_BOI_THUONG');

    await as('quanly01');
    const small = await api.submitComplaint({ type: 'MAT_DO', description: 'Mất mũ' }).catch(() => null);
    expect(small).toBeNull();                                                                          // quản lý không gửi khiếu nại được
    await as('sv01');
    const lost = await api.submitComplaint({ type: 'MAT_DO', description: 'Mất mũ' });
    await as('quanly01');
    await api.startComplaint(lost.id);
    await api.proposeCompensation(lost.id, 2000, 'Hỗ trợ một phần');

    await as('chutk01');
    const first = await api.approveCompensation(lost.id, 'Đồng ý');
    expect(first).toMatchObject({ replayed: false, complaint: { status: 'DA_BOI_THUONG' } });
    expect((await api.approveCompensation(lost.id, 'Đồng ý')).replayed).toBe(true);
    expect((await api.ownerOverview()).balance).toBe(2000);
    expect(await codeOf(api.rejectCompensation(damage.id, ' '))).toBe('VALIDATION_ERROR');
    expect((await api.rejectCompensation(damage.id, 'Thiếu chứng cứ')).status).toBe('KHONG_BOI_THUONG');
  });

  it('sinh viên hủy khi chưa tiếp nhận; tối đa 5 khiếu nại đang mở', async () => {
    await as('sv01');
    const ids: number[] = [];
    for (let i = 0; i < 5; i += 1) ids.push((await api.submitComplaint({ type: 'KHAC', description: `Góp ý ${i}` })).id);
    expect(await codeOf(api.submitComplaint({ type: 'KHAC', description: 'Thứ sáu' }))).toBe('TOO_MANY_OPEN_COMPLAINTS');
    expect((await api.cancelComplaint(ids[0] ?? 0)).status).toBe('DA_HUY');
    expect(await codeOf(api.cancelComplaint(ids[0] ?? 0))).toBe('COMPLAINT_STATUS_CONFLICT');
    expect((await api.submitComplaint({ type: 'KHAC', description: 'Thứ sáu' })).status).toBe('MOI');
  });

  it('quản lý xử lý sự cố bảo vệ báo', async () => {
    await as('baove01');
    const incident = await api.reportIncident({ type: 'XE_GUI_QUA_LAU', plateNumber: '29T67021', description: 'Xe gửi 2 ngày' });
    await as('quanly01');
    expect((await api.managerIncidents()).map((i) => i.id)).toContain(incident.id);
    expect(await codeOf(api.startIncident(9999))).toBe('INCIDENT_NOT_FOUND');
    expect((await api.startIncident(incident.id)).status).toBe('DANG_XU_LY');
    expect(await codeOf(api.startIncident(incident.id))).toBe('INCIDENT_STATUS_CONFLICT');
    const done = await api.resolveIncident(incident.id, 'Đã gọi chủ xe');
    expect(done).toMatchObject({ status: 'DA_XU_LY', handlerName: 'Phạm Văn Quản', resolution: 'Đã gọi chủ xe' });
  });

  it('phân ca: xếp ca ngày mai, không xếp trùng / ngày đã qua; bảo vệ thấy lịch; gỡ được ca chưa bắt đầu', async () => {
    await as('quanly01');
    const tomorrow = dayAfter(1);
    const shift = await api.assignShift({ date: tomorrow, slot: 'CHIEU', guardId: 4 });
    expect(shift).toMatchObject({ attendance: 'SAP_TOI', guardName: 'Trần Văn Bảo' });
    expect(shift.startsAt).toBe(`${tomorrow}T14:00:00+07:00`);
    expect(await codeOf(api.assignShift({ date: tomorrow, slot: 'CHIEU', guardId: 4 }))).toBe('SHIFT_ALREADY_ASSIGNED');
    expect(await codeOf(api.assignShift({ date: dayAfter(-2), slot: 'SANG', guardId: 4 }))).toBe('SHIFT_SLOT_PASSED');
    const night = await api.assignShift({ date: tomorrow, slot: 'DEM', guardId: 4 });
    expect(night.endsAt).toBe(`${dayAfter(2)}T06:00:00+07:00`);                                       // ca đêm sang hôm sau
    expect((await api.shiftWeek()).shifts).toHaveLength(2);

    await as('baove01');
    expect((await api.mySchedule()).map((s) => s.slot)).toEqual(['CHIEU', 'DEM']);

    await as('quanly01');
    await api.removeShift(night.id);
    expect((await api.shiftWeek()).shifts.map((s) => s.id)).toEqual([shift.id]);
  });
});
