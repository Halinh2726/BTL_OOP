import type {
  AuditItem,
  RevenueDay,
  WithdrawalView,
  IncidentItem,
  ParkedVehicleItem,
  ShiftSummary,
  ShiftView,
  FeeRule,
  FieldViolation,
  Guard,
  MeResponse,
  Profile,
  Role,
  TransactionItem,
  Vehicle,
  VehicleType,
} from '../../types/api';
import type { ApiClient, Idempotent } from '../ApiClient';
import { ApiError } from '../ApiError';
import { endsSession, notifySessionEnded } from '../sessionEvents';
import {
  MOCK_OTP,
  OWNER_ID,
  seedState,
  toVietnamIso,
  vietnamClock,
  type MockAuditEntry,
  type MockIncident,
  type MockShift,
  type MockWithdrawal,
  type MockEntry,
  type MockState,
  type MockUser,
  type MockVehicle,
} from './mockState';

/** Cùng mã lỗi, HTTP status và lời nhắn với ErrorCode của backend. */
const ERRORS = {
  VALIDATION_ERROR: [400, 'Dữ liệu không hợp lệ.'],
  IDEMPOTENCY_KEY_REQUIRED: [400, 'Thiếu khóa chống xử lý trùng.'],
  UNAUTHENTICATED: [401, 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'],
  INVALID_CREDENTIALS: [401, 'Sai tên đăng nhập hoặc mật khẩu'],
  ACCOUNT_LOCKED: [403, 'Tài khoản đã bị khóa. Vui lòng liên hệ quản lý.'],
  FORBIDDEN: [403, 'Bạn không có quyền thực hiện thao tác này.'],
  PASSWORD_CHANGE_REQUIRED: [403, 'Bạn cần đổi mật khẩu tạm trước khi tiếp tục.'],
  VEHICLE_NOT_FOUND: [404, 'Không tìm thấy xe.'],
  EMAIL_EXISTS: [409, 'Email đã được dùng cho tài khoản khác'],
  VEHICLE_PARKED: [409, 'Xe đang trong bãi. Lấy xe ra trước khi sửa hoặc ngừng sử dụng.'],
  VEHICLE_INACTIVE: [409, 'Xe đã ngừng sử dụng.'],
  PASSWORD_POLICY: [422, 'Mật khẩu phải dài 8–72 ký tự.'],
  PASSWORD_REUSED: [422, 'Mật khẩu mới phải khác mật khẩu hiện tại.'],
  CURRENT_PASSWORD_INCORRECT: [422, 'Mật khẩu hiện tại không đúng.'],
  RESET_CODE_INVALID: [422, 'Mã xác nhận không đúng.'],
  RESET_CODE_EXPIRED: [422, 'Mã xác nhận đã hết hạn hoặc đã được dùng. Vui lòng yêu cầu mã mới.'],
  RESET_CODE_LOCKED: [422, 'Nhập sai mã quá 5 lần. Vui lòng yêu cầu mã mới.'],
  PLATE_REQUIRED: [422, 'Vui lòng nhập biển số. Chỉ xe đạp không có biển mới được bãi cấp mã.'],
  VEHICLE_LIMIT_REACHED: [422, 'Mỗi sinh viên được tối đa 3 xe đang sử dụng.'],
  NO_OPEN_SHIFT: [409, 'Bạn chưa nhận ca trực.'],
  HANDOVER_PENDING: [409, 'Có biên bản bàn giao ca chưa được xác nhận. Xem lại rồi bấm Nhận ca.'],
  INCIDENT_NOT_FOUND: [404, 'Không tìm thấy sự cố.'],
  PHOTO_INVALID: [422, 'Ảnh phải là JPG, PNG hoặc WEBP và không quá 2 MB.'],
  SELF_PROCESSING_FORBIDDEN: [403, 'Người tạo yêu cầu không được tự xử lý yêu cầu của mình.'],
  WITHDRAWAL_NOT_FOUND: [404, 'Không tìm thấy yêu cầu rút điểm.'],
  TRANSACTION_NOT_FOUND: [404, 'Không tìm thấy giao dịch gốc.'],
  WITHDRAWAL_STATUS_CONFLICT: [409, 'Yêu cầu rút điểm không ở trạng thái cho phép thao tác này.'],
  WITHDRAWAL_EXCEEDS_AVAILABLE: [422, 'Số điểm yêu cầu vượt số điểm còn có thể rút (đã trừ các yêu cầu đang chờ).'],
  CASH_FUND_INSUFFICIENT: [422, 'Quỹ tiền mặt hiện không đủ để trả yêu cầu này.'],
  PLATE_NOT_FOUND: [404, 'Biển số chưa được đăng ký.'],
  NO_OPEN_SESSION: [404, 'Xe không có lượt gửi đang mở.'],
  STUDENT_NOT_FOUND: [404, 'MSSV không tồn tại'],
  GUARD_NOT_FOUND: [404, 'Không tìm thấy tài khoản bảo vệ.'],
  STUDENT_CODE_EXISTS: [409, 'MSSV đã tồn tại'],
  USERNAME_EXISTS: [409, 'Tên đăng nhập đã tồn tại'],
  PLATE_EXISTS: [409, 'Biển số đã được đăng ký'],
  SESSION_ALREADY_OPEN: [409, 'Xe đang có lượt gửi chưa kết thúc. Không cho xe vào.'],
  SESSION_ALREADY_PAID: [409, 'Lượt gửi này đã được thanh toán. Không trừ điểm lần hai.'],
  INSUFFICIENT_POINTS: [422, 'Không đủ điểm.'],
  INVALID_AMOUNT: [422, 'Số tiền hoặc số điểm không hợp lệ.'],
  IDEMPOTENCY_KEY_REUSED: [422, 'Khóa chống xử lý trùng đã được dùng cho yêu cầu khác.'],
  TOO_MANY_REQUESTS: [429, 'Thao tác quá nhiều lần. Vui lòng thử lại sau ít phút.'],
} as const satisfies Record<string, readonly [number, string]>;

type ErrorCode = keyof typeof ERRORS;

const PERMISSIONS: Record<Role, string[]> = {
  SINH_VIEN: ['VEHICLE_REGISTER', 'WALLET_VIEW_OWN'],
  BAO_VE: ['INCIDENT_REPORT', 'PARKING_CHECK_IN', 'PARKING_CHECK_OUT'],
  QUAN_LY: ['ACCOUNT_MANAGE', 'CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'GUARD_CREATE', 'GUARD_LOCK', 'POINT_ADJUST'],
  CHU_TAI_KHOAN: ['OWNER_FINANCE', 'WALLET_VIEW_OWN'],
};

const VEHICLE_TYPES: VehicleType[] = [
  { code: 'XE_MAY', name: 'Xe máy' },
  { code: 'XE_DAP', name: 'Xe đạp' },
  { code: 'XE_DAP_DIEN', name: 'Xe đạp điện' },
];
const FEE_RULES: FeeRule[] = [
  { startTime: '00:00', feePoints: 3000 },
  { startTime: '18:00', feePoints: 5000 },
];
const CASH_PER_POINT = 1;
const MAX_AMOUNT = 10_000_000;
const LOGIN_MAX_FAILURES = 5;
const LOGIN_WINDOW_MS = 60_000;
const MAX_SUGGESTIONS = 8;

const USERNAME = /^[a-z0-9._]{4,50}$/;
const PLATE = /^[A-Z0-9]{5,20}$/;
const STUDENT_CODE = /^[A-Z0-9]{1,20}$/;
const PHONE = /^0[0-9]{9,10}$/;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const STANDARD_PLATE = /^([0-9]{2}[A-Z]{1,2}[0-9]{4,6}|XD[0-9]{5})$/;
const MAX_ACTIVE_VEHICLES = 3;
const RESET_CODE_TTL_MS = 10 * 60_000;
const TEMPORARY_PASSWORD = 'TamThoi2345';
const STORAGE_KEY = 'parking-mock-state';

function fail(code: ErrorCode, retryAfterSeconds: number | null = null): never {
  const [status, message] = ERRORS[code];
  throw new ApiError({ code, message, status, traceId: 'mock' }, retryAfterSeconds);
}

function invalid(fields: FieldViolation[]): void {
  if (fields.length > 0) {
    const [status, message] = ERRORS.VALIDATION_ERROR;
    throw new ApiError({ code: 'VALIDATION_ERROR', message, status, traceId: 'mock', fields });
  }
}

/** Biển số: bỏ khoảng trắng, dấu chấm, gạch ngang rồi viết hoa — cùng quy tắc với PlateNumber của backend. */
function normalizePlate(raw: string): string {
  return raw.replace(/[\s.-]/g, '').toUpperCase();
}

function feeAt(time: Date): number {
  const clock = vietnamClock(time);
  return FEE_RULES.filter((rule) => rule.startTime <= clock).reduce((_, rule) => rule.feePoints, FEE_RULES[0]?.feePoints ?? 0);
}

const MIN_FEE = Math.min(...FEE_RULES.map((rule) => rule.feePoints));

/** Cùng quy tắc PlateNumber.display() của backend. */
function displayPlate(plate: string): string {
  const bicycle = /^XD([0-9]{5})$/.exec(plate);
  if (bicycle) return `XD-${bicycle[1]}`;
  const parts = /^([0-9]{2}[A-Z]{1,2})([0-9]{4,6})$/.exec(plate);
  if (!parts) return plate;
  let series = parts[1] ?? '';
  let digits = parts[2] ?? '';
  if (digits.length === 6) {
    series += digits.charAt(0);
    digits = digits.slice(1);
  }
  return digits.length === 5 ? `${series}-${digits.slice(0, 3)}.${digits.slice(3)}` : `${series}-${digits}`;
}

function isActive(vehicle: MockVehicle): boolean {
  return (vehicle.status ?? 'DANG_DUNG') === 'DANG_DUNG';
}

function toVehicle(vehicle: MockVehicle): Vehicle {
  return {
    id: vehicle.id,
    plateNumber: vehicle.plateNumber,
    plateDisplay: displayPlate(vehicle.plateNumber),
    vehicleTypeCode: vehicle.vehicleTypeCode,
    status: vehicle.status ?? 'DANG_DUNG',
    registeredAt: vehicle.registeredAt,
    deactivatedAt: vehicle.deactivatedAt ?? null,
  };
}

function loadState(): MockState {
  try {
    const saved = sessionStorage.getItem(STORAGE_KEY);
    if (saved) return JSON.parse(saved) as MockState;
  } catch {
    // Không đọc được (chế độ riêng tư...) -> dùng dữ liệu mẫu
  }
  return seedState();
}

/**
 * Backend giả lập trong trình duyệt: cùng hình dạng JSON, cùng mã lỗi, có trạng thái — xe vào tạo lượt gửi,
 * xe ra trừ điểm sinh viên và cộng cho chủ tài khoản, gửi lại cùng Idempotency-Key trả lại kết quả cũ,
 * khóa bảo vệ thì bảo vệ đó không đăng nhập được. Trạng thái giữ trong sessionStorage của tab.
 */
export function createMockApiClient(options: { latencyMs?: number; initialState?: MockState } = {}): ApiClient {
  const latencyMs = options.latencyMs ?? 250;
  const state: MockState = options.initialState ?? loadState();

  function save(): void {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      // Bỏ qua: chỉ mất dữ liệu giả lập khi tải lại trang
    }
  }

  /** Chạy một "request": trễ như mạng thật, trả về bản sao để giao diện không sửa được trạng thái. */
  async function call<T>(handler: () => T): Promise<T> {
    await new Promise((resolve) => setTimeout(resolve, latencyMs));
    try {
      const result = handler();
      save();
      return result === undefined ? result : (structuredClone(result) as T);
    } catch (error) {
      save();
      if (error instanceof ApiError && endsSession(error.code)) notifySessionEnded(error);
      throw error;
    }
  }

  function nextId(): number {
    state.nextId += 1;
    return state.nextId;
  }

  function currentUser(): MockUser {
    const user = state.users.find((candidate) => candidate.id === state.currentUserId);
    if (!user) return fail('UNAUTHENTICATED');
    if (user.status === 'BI_KHOA') {
      state.currentUserId = null;
      return fail('ACCOUNT_LOCKED');
    }
    return user;
  }

  /** Như CredentialFreshnessFilter của backend: đang dùng mật khẩu tạm thì chỉ được đổi mật khẩu. */
  function requireRole(...roles: Role[]): MockUser {
    const user = currentUser();
    if (user.mustChangePassword) fail('PASSWORD_CHANGE_REQUIRED');
    if (!roles.includes(user.role)) fail('FORBIDDEN');
    return user;
  }

  function meOf(user: MockUser): MeResponse {
    return {
      user: { id: user.id, username: user.username, fullName: user.fullName, role: user.role },
      permissions: PERMISSIONS[user.role],
      mustChangePassword: user.mustChangePassword ?? false,
    };
  }

  function audit(actorId: number, action: MockAuditEntry['action'], targetUserId: number | null, detail: string) {
    state.auditLogs = state.auditLogs ?? [];
    state.auditLogs.push({ id: nextId(), actorId, action, targetUserId, detail, createdAt: toVietnamIso(new Date()) });
  }

  function profileOf(user: MockUser): Profile {
    return {
      userId: user.id,
      username: user.username,
      role: user.role,
      fullName: user.fullName,
      email: user.email ?? null,
      phone: user.role === 'SINH_VIEN' || user.role === 'BAO_VE' ? (user.phone ?? null) : null,
      studentCode: user.studentCode ?? null,
      phoneEditable: user.role === 'SINH_VIEN' || user.role === 'BAO_VE',
      phoneRequired: user.role === 'BAO_VE',
    };
  }

  function checkNewPassword(user: MockUser, newPassword: string): void {
    if (newPassword.length < 8 || newPassword.length > 72) fail('PASSWORD_POLICY');
    if (newPassword === user.password) fail('PASSWORD_REUSED');
  }

  function ownedIdleVehicle(user: MockUser, vehicleId: number): MockVehicle {
    const vehicle = state.vehicles.find((candidate) => candidate.id === vehicleId && candidate.userId === user.id);
    if (!vehicle) return fail('VEHICLE_NOT_FOUND');
    if (!isActive(vehicle)) fail('VEHICLE_INACTIVE');
    if (openSessionOf(vehicle.id)) fail('VEHICLE_PARKED');
    return vehicle;
  }

  // ===== Đợt 2: bảo vệ =====

  function nameOf(userId: number): string | null {
    return state.users.find((user) => user.id === userId)?.fullName ?? null;
  }

  function sinceMs(iso: string): number {
    return new Date(iso).getTime();
  }

  function openIncidents(): MockIncident[] {
    return (state.incidents ?? []).filter((incident) => incident.status === 'MOI' || incident.status === 'DANG_XU_LY');
  }

  function summarize(guardId: number, fromIso: string): ShiftSummary {
    const from = sinceMs(fromIso);
    return {
      checkIns: state.sessions.filter((s) => s.entryGuardId === guardId && sinceMs(s.enteredAt) >= from).length,
      checkOuts: state.sessions.filter((s) => s.exitGuardId === guardId && s.exitedAt && sinceMs(s.exitedAt) >= from).length,
      vehiclesInLot: state.sessions.filter((s) => s.exitedAt === null).length,
      openIncidents: openIncidents().length,
    };
  }

  function toShiftView(shift: MockShift): ShiftView {
    const { id, guardId, startedAt, endedAt, summary, handoverNote } = shift;
    const openingCash = shift.openingCash ?? 0;
    const expected = shift.expectedOpeningCash ?? null;
    const end = endedAt ? sinceMs(endedAt) : Date.now();
    return {
      id, guardId, guardName: nameOf(guardId), startedAt, endedAt, summary, handoverNote,
      workedMinutes: Math.max(0, Math.floor((end - sinceMs(startedAt)) / 60_000)),
      openingCash, expectedOpeningCash: expected, openingDiscrepancy: expected === null ? null : openingCash - expected,
      closingCash: shift.closingCash ?? (endedAt ? 0 : null),
      receivedByName: shift.receivedBy ? nameOf(shift.receivedBy) : null, receivedAt: shift.receivedAt ?? null,
    };
  }

  function checkCash(amount: number): void {
    if (!Number.isSafeInteger(amount) || amount < 0 || amount > 50_000_000) fail('INVALID_AMOUNT');
  }

  function toIncident(incident: MockIncident): IncidentItem {
    return {
      id: incident.id, type: incident.type, plateNumber: incident.plateNumber,
      plateDisplay: incident.plateNumber ? displayPlate(incident.plateNumber) : null,
      description: incident.description, status: incident.status, createdAt: incident.createdAt,
      reporterName: nameOf(incident.reporterId), hasPhoto: incident.photo !== null, resolution: incident.resolution,
    };
  }

  // ===== Đợt 3: tài chính chủ bãi xe =====

  /** R13: tiền mặt quản lý đang giữ = tiền nhận khi đổi điểm − tiền trả khi rút (tỷ lệ 1 đ = 1 điểm). */
  function cashHeld(): number {
    return state.transactions.reduce((sum, t) => {
      if (t.type === 'DOI_DIEM') return sum + (t.entries[0]?.change ?? 0) * CASH_PER_POINT;
      if (t.type === 'RUT_DIEM') return sum + (t.entries[0]?.change ?? 0) * CASH_PER_POINT;
      return sum;
    }, 0);
  }

  function pendingPoints(): number {
    return (state.withdrawals ?? []).filter((w) => w.status === 'CHO_XU_LY').reduce((sum, w) => sum + w.points, 0);
  }

  function toWithdrawal(w: MockWithdrawal): WithdrawalView {
    return {
      ...w, cashPerPoint: CASH_PER_POINT, cashAmount: w.points * CASH_PER_POINT, method: 'TIEN_MAT',
      processedByName: w.processedBy ? nameOf(w.processedBy) : null,
      resolvedByName: w.resolvedBy ? nameOf(w.resolvedBy) : null,
    };
  }

  function withdrawalOf(id: number): MockWithdrawal {
    const found = (state.withdrawals ?? []).find((w) => w.id === id);
    if (!found) return fail('WITHDRAWAL_NOT_FOUND');
    return found;
  }

  function moveWithdrawal(w: MockWithdrawal, from: MockWithdrawal['status'][], to: MockWithdrawal['status']): void {
    if (!from.includes(w.status)) fail('WITHDRAWAL_STATUS_CONFLICT');
    w.status = to;
  }

  function requireNote(note: string, field = 'note'): string {
    const text = note.trim();
    if (!text || text.length > 500) invalid([{ field, code: 'LENGTH', message: 'Vui lòng ghi lý do (tối đa 500 ký tự)' }]);
    return text;
  }

  function dailyRevenue(from: string, to: string): RevenueDay[] {
    const days = new Map<string, RevenueDay>();
    state.transactions
      .filter((t) => t.createdAt.slice(0, 10) >= from && t.createdAt.slice(0, 10) <= to)
      .forEach((t) => {
        const date = t.createdAt.slice(0, 10);
        const day = days.get(date) ?? { date, payments: 0, revenue: 0, adjustments: 0, cashIn: 0, cashOut: 0 };
        const ownerChange = t.entries.find((entry) => entry.userId === OWNER_ID)?.change ?? 0;
        if (t.type === 'THANH_TOAN') {
          day.payments += 1;
          day.revenue += ownerChange;
        } else if (t.type === 'DIEU_CHINH') {
          day.adjustments += ownerChange;
        } else if (t.type === 'DOI_DIEM') {
          day.cashIn += (t.entries[0]?.change ?? 0) * CASH_PER_POINT;
        } else if (t.type === 'RUT_DIEM') {
          day.cashOut += -(t.entries[0]?.change ?? 0) * CASH_PER_POINT;
        }
        days.set(date, day);
      });
    return [...days.values()].sort((a, b) => a.date.localeCompare(b.date));
  }

  function issueTemporary(managerId: number, user: MockUser) {
    user.password = TEMPORARY_PASSWORD;
    user.mustChangePassword = true;
    audit(managerId, 'CAP_MAT_KHAU_TAM', user.id, `Cấp mật khẩu tạm cho ${user.username}`);
    return { userId: user.id, username: user.username, fullName: user.fullName, temporaryPassword: TEMPORARY_PASSWORD };
  }

  function balanceOf(userId: number): number {
    return state.wallets[userId] ?? 0;
  }

  /** Ghi một giao dịch: cập nhật số dư từng ví và lưu bút toán. */
  function record(type: MockState['transactions'][number]['type'], changes: Array<[number, number]>, now: Date) {
    const entries: MockEntry[] = changes.map(([userId, change]) => {
      const balanceBefore = balanceOf(userId);
      const balanceAfter = balanceBefore + change;
      state.wallets[userId] = balanceAfter;
      return { userId, change, balanceBefore, balanceAfter };
    });
    const transaction = { id: nextId(), type, createdAt: toVietnamIso(now), entries };
    state.transactions.push(transaction);
    return transaction;
  }

  /** Mỗi Idempotency-Key tạo nhiều nhất một giao dịch; cùng key khác nội dung -> IDEMPOTENCY_KEY_REUSED. */
  function idempotent<T>(key: string, fingerprint: string, work: () => T): Idempotent<T> {
    if (!key) fail('IDEMPOTENCY_KEY_REQUIRED');
    const previous = state.idempotency[key];
    if (previous) {
      if (previous.fingerprint !== fingerprint) fail('IDEMPOTENCY_KEY_REUSED');
      return { data: previous.result as T, replayed: true };
    }
    const result = work();
    state.idempotency[key] = { fingerprint, result };
    return { data: result, replayed: false };
  }

  function findOwnerByPlate(plate: string) {
    const vehicle = state.vehicles.find((candidate) => candidate.plateNumber === plate && isActive(candidate));
    const owner = vehicle ? state.users.find((user) => user.id === vehicle.userId) : undefined;
    return vehicle && owner ? { vehicle, owner } : null;
  }

  function openSessionOf(vehicleId: number) {
    return state.sessions.find((session) => session.vehicleId === vehicleId && session.exitedAt === null);
  }

  function parsePlate(raw: string): string {
    const plate = normalizePlate(raw);
    if (!PLATE.test(plate)) fail('VALIDATION_ERROR');
    return plate;
  }

  function toGuard(user: MockUser): Guard {
    return { id: user.id, username: user.username, fullName: user.fullName, phone: user.phone ?? '', status: user.status };
  }

  function usernameTaken(username: string): boolean {
    return state.users.some((user) => user.username === username);
  }

  return {
    login: (body) =>
      call(() => {
        const key = body.username.toLowerCase();
        const now = Date.now();
        const recent = (state.loginFailures[key] ?? []).filter((time) => now - time < LOGIN_WINDOW_MS);
        state.loginFailures[key] = recent;
        if (recent.length >= LOGIN_MAX_FAILURES) {
          fail('TOO_MANY_REQUESTS', Math.ceil((LOGIN_WINDOW_MS - (now - (recent[0] ?? now))) / 1000));
        }
        const user = state.users.find((candidate) => candidate.username === body.username);
        if (!user || user.password !== body.password) {
          recent.push(now);
          return fail('INVALID_CREDENTIALS');
        }
        if (user.status === 'BI_KHOA') fail('ACCOUNT_LOCKED');
        delete state.loginFailures[key];
        state.currentUserId = user.id;
        return meOf(user);
      }),

    logout: () =>
      call(() => {
        state.currentUserId = null;
      }),

    me: () => call(() => meOf(currentUser())),

    registerStudent: (body) =>
      call(() => {
        const studentCode = body.studentCode.trim().toUpperCase();
        const fullName = body.fullName.trim();
        const fields: FieldViolation[] = [];
        if (!STUDENT_CODE.test(studentCode)) {
          fields.push({ field: 'studentCode', code: 'PATTERN', message: 'MSSV chỉ gồm chữ và số, tối đa 20 ký tự' });
        }
        if (!fullName || fullName.length > 100) {
          fields.push({ field: 'fullName', code: 'LENGTH', message: 'Họ tên tối đa 100 ký tự' });
        }
        if (!USERNAME.test(body.username)) {
          fields.push({
            field: 'username', code: 'PATTERN',
            message: 'Tên đăng nhập 4–50 ký tự, chỉ gồm chữ thường, số, dấu chấm, gạch dưới',
          });
        }
        if (body.password.length < 8 || body.password.length > 72) {
          fields.push({ field: 'password', code: 'LENGTH', message: 'Mật khẩu từ 8 đến 72 ký tự' });
        }
        invalid(fields);
        if (state.users.some((user) => user.studentCode === studentCode)) fail('STUDENT_CODE_EXISTS');
        if (usernameTaken(body.username)) fail('USERNAME_EXISTS');
        const user: MockUser = {
          id: nextId(), username: body.username, password: body.password, fullName, role: 'SINH_VIEN',
          status: 'HOAT_DONG', studentCode,
        };
        state.users.push(user);
        state.wallets[user.id] = 0;
        return { id: user.id, username: user.username, fullName, studentCode };
      }),

    vehicleTypes: () => call(() => VEHICLE_TYPES),
    feeRules: () => call(() => FEE_RULES),
    exchangeRate: () => call(() => ({ cashPerPoint: CASH_PER_POINT })),

    myVehicles: () =>
      call(() => {
        const user = requireRole('SINH_VIEN');
        return state.vehicles
          .filter((vehicle) => vehicle.userId === user.id)
          .sort((a, b) => Number(isActive(b)) - Number(isActive(a)) || b.registeredAt.localeCompare(a.registeredAt) || b.id - a.id)
          .map(toVehicle);
      }),

    registerVehicle: (body) =>
      call(() => {
        const user = requireRole('SINH_VIEN');
        let plateNumber = normalizePlate(body.plateNumber);
        const fields: FieldViolation[] = [];
        if (plateNumber !== '' && !STANDARD_PLATE.test(plateNumber)) {
          fields.push({
            field: 'plateNumber', code: 'PATTERN',
            message: 'Biển số theo dạng 29A-1514 hoặc 29A-123.45 (tối đa 5 số cuối)',
          });
        }
        if (!VEHICLE_TYPES.some((type) => type.code === body.vehicleTypeCode)) {
          fields.push({ field: 'vehicleTypeCode', code: 'NOT_FOUND', message: 'Loại xe không tồn tại' });
        }
        invalid(fields);
        if (plateNumber === '' && body.vehicleTypeCode !== 'XE_DAP') fail('PLATE_REQUIRED');
        const active = state.vehicles.filter((vehicle) => vehicle.userId === user.id && isActive(vehicle)).length;
        if (active >= MAX_ACTIVE_VEHICLES) fail('VEHICLE_LIMIT_REACHED');
        if (plateNumber === '') {
          state.bicycleSeq = (state.bicycleSeq ?? 0) + 1;
          plateNumber = `XD${String(state.bicycleSeq).padStart(5, '0')}`;
        }
        if (state.vehicles.some((vehicle) => vehicle.plateNumber === plateNumber && isActive(vehicle))) fail('PLATE_EXISTS');
        const vehicle: MockVehicle = {
          id: nextId(), userId: user.id, plateNumber, vehicleTypeCode: body.vehicleTypeCode,
          registeredAt: toVietnamIso(new Date()), status: 'DANG_DUNG', deactivatedAt: null,
        };
        state.vehicles.push(vehicle);
        return toVehicle(vehicle);
      }),

    myWallet: () =>
      call(() => {
        const user = requireRole('SINH_VIEN', 'CHU_TAI_KHOAN');
        return { balance: balanceOf(user.id) };
      }),

    myTransactions: (query) =>
      call(() => {
        const user = requireRole('SINH_VIEN', 'CHU_TAI_KHOAN');
        const limit = query.limit ?? 20;
        const mine: TransactionItem[] = state.transactions
          .filter((transaction) => !query.type || transaction.type === query.type)
          .filter((transaction) => query.before === undefined || transaction.id < query.before)
          .sort((a, b) => b.id - a.id)
          .flatMap((transaction) =>
            transaction.entries
              .filter((entry) => entry.userId === user.id)
              .map(({ change, balanceBefore, balanceAfter }) => ({
                id: transaction.id, type: transaction.type, change, balanceBefore, balanceAfter,
                createdAt: transaction.createdAt,
              })),
          );
        const items = mine.slice(0, limit);
        const hasMore = mine.length > limit;
        return { items, hasMore, nextBefore: hasMore ? (items[items.length - 1]?.id ?? null) : null };
      }),

    suggestPlates: (rawFragment, parkedOnly) =>
      call(() => {
        requireRole('BAO_VE');
        const fragment = normalizePlate(rawFragment);
        if (!/^[A-Z0-9]{2,20}$/.test(fragment)) fail('VALIDATION_ERROR');
        const parked = (vehicleId: number) => openSessionOf(vehicleId) !== undefined;
        return state.vehicles
          .filter((vehicle) => isActive(vehicle) && vehicle.plateNumber.includes(fragment))
          .filter((vehicle) => !parkedOnly || parked(vehicle.id))
          .sort(
            (a, b) =>
              Number(b.plateNumber.endsWith(fragment)) - Number(a.plateNumber.endsWith(fragment)) ||
              a.plateNumber.localeCompare(b.plateNumber),
          )
          .slice(0, MAX_SUGGESTIONS)
          .map(({ id, plateNumber, vehicleTypeCode }) => ({ plateNumber, vehicleTypeCode, parked: parked(id) }));
      }),

    previewCheckIn: (rawPlate) =>
      call(() => {
        requireRole('BAO_VE');
        const plate = parsePlate(rawPlate);
        const found = findOwnerByPlate(plate);
        if (!found) {
          return {
            plateNumber: null, vehicleTypeCode: null, studentCode: null, fullName: null, balance: null,
            minFee: MIN_FEE, allowed: false, reason: 'PLATE_NOT_FOUND' as const,
          };
        }
        const balance = balanceOf(found.owner.id);
        const reason = openSessionOf(found.vehicle.id)
          ? ('SESSION_ALREADY_OPEN' as const)
          : balance < MIN_FEE ? ('INSUFFICIENT_POINTS' as const) : null;
        return {
          plateNumber: plate, vehicleTypeCode: found.vehicle.vehicleTypeCode,
          studentCode: found.owner.studentCode ?? null, fullName: found.owner.fullName, balance,
          minFee: MIN_FEE, allowed: reason === null, reason,
        };
      }),

    checkIn: (rawPlate) =>
      call(() => {
        requireRole('BAO_VE');
        const plate = normalizePlate(rawPlate);
        invalid(PLATE.test(plate) ? [] : [{ field: 'plateNumber', code: 'PATTERN', message: 'Biển số 5–20 ký tự, chỉ gồm chữ và số' }]);
        const found = findOwnerByPlate(plate);
        if (!found) return fail('PLATE_NOT_FOUND');
        if (openSessionOf(found.vehicle.id)) fail('SESSION_ALREADY_OPEN');
        if (balanceOf(found.owner.id) < MIN_FEE) fail('INSUFFICIENT_POINTS');
        const guard = currentUser();
        const session = {
          id: nextId(), vehicleId: found.vehicle.id, enteredAt: toVietnamIso(new Date()), exitedAt: null,
          entryGuardId: guard.id, exitGuardId: null,
        };
        state.sessions.push(session);
        return { sessionId: session.id, plateNumber: plate, enteredAt: session.enteredAt };
      }),

    previewCheckOut: (rawPlate) =>
      call(() => {
        requireRole('BAO_VE');
        const plate = parsePlate(rawPlate);
        const found = findOwnerByPlate(plate);
        if (!found) {
          return {
            sessionId: null, plateNumber: null, vehicleTypeCode: null, studentCode: null, fullName: null,
            enteredAt: null, balance: null, fee: null, allowed: false, reason: 'PLATE_NOT_FOUND' as const,
          };
        }
        const owner = {
          plateNumber: plate, vehicleTypeCode: found.vehicle.vehicleTypeCode,
          studentCode: found.owner.studentCode ?? null, fullName: found.owner.fullName,
        };
        const balance = balanceOf(found.owner.id);
        const session = openSessionOf(found.vehicle.id);
        if (!session) {
          return {
            ...owner, sessionId: null, enteredAt: null, balance, fee: null, allowed: false,
            reason: 'NO_OPEN_SESSION' as const,
          };
        }
        const fee = feeAt(new Date());
        const reason = balance < fee ? ('INSUFFICIENT_POINTS' as const) : null;
        return { ...owner, sessionId: session.id, enteredAt: session.enteredAt, balance, fee, allowed: reason === null, reason };
      }),

    checkOut: (sessionId, key) =>
      call(() => {
        const guard = requireRole('BAO_VE');
        return idempotent(key, `THANH_TOAN|${guard.id}|${sessionId}`, () => {
          const session = state.sessions.find((candidate) => candidate.id === sessionId);
          if (!session) return fail('NO_OPEN_SESSION');
          if (session.exitedAt !== null) fail('SESSION_ALREADY_PAID');
          const vehicle = state.vehicles.find((candidate) => candidate.id === session.vehicleId);
          if (!vehicle) return fail('NO_OPEN_SESSION');
          const now = new Date();
          const fee = feeAt(now);
          if (balanceOf(vehicle.userId) < fee) fail('INSUFFICIENT_POINTS');
          session.exitedAt = toVietnamIso(now);
          session.exitGuardId = guard.id;
          const transaction = record('THANH_TOAN', [[vehicle.userId, -fee], [OWNER_ID, fee]], now);
          return {
            transactionId: transaction.id, sessionId, fee, exitedAt: transaction.createdAt,
            balanceAfter: balanceOf(vehicle.userId),
          };
        });
      }),

    lookupStudent: (rawCode) =>
      call(() => {
        requireRole('QUAN_LY');
        const studentCode = rawCode.trim().toUpperCase();
        const student = state.users.find((user) => user.studentCode === studentCode);
        if (!student) return fail('STUDENT_NOT_FOUND');
        return { studentCode, fullName: student.fullName, balance: balanceOf(student.id) };
      }),

    deposit: (body, key) =>
      call(() => {
        const manager = requireRole('QUAN_LY');
        if (body.cashAmount > MAX_AMOUNT) {
          invalid([{ field: 'cashAmount', code: 'RANGE', message: 'Tối đa 10.000.000 đ mỗi lần' }]);
        }
        const studentCode = body.studentCode.trim().toUpperCase();
        return idempotent(key, `DOI_DIEM|${manager.id}|${studentCode}|${body.cashAmount}`, () => {
          const student = state.users.find((user) => user.studentCode === studentCode);
          if (!student) return fail('STUDENT_NOT_FOUND');
          if (body.cashAmount <= 0 || body.cashAmount % CASH_PER_POINT !== 0) fail('INVALID_AMOUNT');
          const points = body.cashAmount / CASH_PER_POINT;
          const transaction = record('DOI_DIEM', [[student.id, points]], new Date());
          const entry = transaction.entries[0];
          return {
            transactionId: transaction.id, studentCode, cashAmount: body.cashAmount, cashPerPoint: CASH_PER_POINT,
            points, balanceBefore: entry?.balanceBefore ?? 0, balanceAfter: entry?.balanceAfter ?? 0,
            createdAt: transaction.createdAt,
          };
        });
      }),

    ownerWallet: () =>
      call(() => {
        requireRole('QUAN_LY');
        const owner = state.users.find((user) => user.id === OWNER_ID);
        return { fullName: owner?.fullName ?? '', balance: balanceOf(OWNER_ID) };
      }),

    guards: () =>
      call(() => {
        requireRole('QUAN_LY');
        return state.users
          .filter((user) => user.role === 'BAO_VE')
          .sort((a, b) => a.fullName.localeCompare(b.fullName, 'vi') || a.id - b.id)
          .map(toGuard);
      }),

    createGuard: (body) =>
      call(() => {
        requireRole('QUAN_LY');
        const fullName = body.fullName.trim();
        const phone = body.phone.trim();
        const fields: FieldViolation[] = [];
        if (!fullName || fullName.length > 100) {
          fields.push({ field: 'fullName', code: 'LENGTH', message: 'Họ tên tối đa 100 ký tự' });
        }
        if (!PHONE.test(phone)) {
          fields.push({ field: 'phone', code: 'PATTERN', message: 'Số điện thoại gồm 10–11 chữ số, bắt đầu bằng 0' });
        }
        if (!USERNAME.test(body.username)) {
          fields.push({
            field: 'username', code: 'PATTERN',
            message: 'Tên đăng nhập 4–50 ký tự, chỉ gồm chữ thường, số, dấu chấm, gạch dưới',
          });
        }
        if (body.password.length < 8 || body.password.length > 72) {
          fields.push({ field: 'password', code: 'LENGTH', message: 'Mật khẩu từ 8 đến 72 ký tự' });
        }
        invalid(fields);
        if (usernameTaken(body.username)) fail('USERNAME_EXISTS');
        const user: MockUser = {
          id: nextId(), username: body.username, password: body.password, fullName, role: 'BAO_VE',
          status: 'HOAT_DONG', phone,
        };
        state.users.push(user);
        return toGuard(user);
      }),

    changeGuardStatus: (guardId, status) =>
      call(() => {
        requireRole('QUAN_LY');
        const guard = state.users.find((user) => user.id === guardId && user.role === 'BAO_VE');
        if (!guard) return fail('GUARD_NOT_FOUND');
        guard.status = status;
        return toGuard(guard);
      }),

    // ===== Đợt 1 =====

    myProfile: () => call(() => profileOf(currentUser())),

    updateMyProfile: (body) =>
      call(() => {
        const user = requireRole('SINH_VIEN', 'BAO_VE', 'QUAN_LY', 'CHU_TAI_KHOAN');
        const fullName = body.fullName.trim().replace(/\s+/g, ' ');
        const email = body.email.trim().toLowerCase();
        const phone = body.phone.replace(/[\s.-]/g, '');
        const fields: FieldViolation[] = [];
        if (!fullName || fullName.length > 100) fields.push({ field: 'fullName', code: 'LENGTH', message: 'Họ tên tối đa 100 ký tự' });
        if (email && !EMAIL.test(email)) fields.push({ field: 'email', code: 'PATTERN', message: 'Email không đúng định dạng' });
        if (phone && !PHONE.test(phone)) {
          fields.push({ field: 'phone', code: 'PATTERN', message: 'Số điện thoại gồm 10–11 chữ số, bắt đầu bằng 0' });
        }
        if (user.role === 'BAO_VE' && !phone) fields.push({ field: 'phone', code: 'REQUIRED', message: 'Bảo vệ bắt buộc có số điện thoại' });
        invalid(fields);
        if (email && state.users.some((other) => other.id !== user.id && other.email === email)) fail('EMAIL_EXISTS');
        user.fullName = fullName;
        user.email = email || undefined;
        if (user.role === 'SINH_VIEN' || user.role === 'BAO_VE') user.phone = phone || undefined;
        audit(user.id, 'CAP_NHAT_THONG_TIN', user.id, 'Tự cập nhật thông tin cá nhân');
        return profileOf(user);
      }),

    changePassword: (body) =>
      call(() => {
        const user = currentUser();
        if (body.currentPassword !== user.password) fail('CURRENT_PASSWORD_INCORRECT');
        checkNewPassword(user, body.newPassword);
        user.password = body.newPassword;
        user.mustChangePassword = false;
        audit(user.id, 'DOI_MAT_KHAU', user.id, 'Tự đổi mật khẩu');
      }),

    requestPasswordReset: (rawEmail) =>
      call(() => {
        const email = rawEmail.trim().toLowerCase();
        const user = state.users.find((candidate) => candidate.email === email && candidate.status === 'HOAT_DONG');
        if (user) {
          state.resetCodes = [
            ...(state.resetCodes ?? []).filter((code) => code.userId !== user.id),
            { userId: user.id, expiresAt: Date.now() + RESET_CODE_TTL_MS, failed: 0, used: false },
          ];
          audit(user.id, 'YEU_CAU_KHOI_PHUC_MAT_KHAU', user.id, 'Gửi mã khôi phục (chế độ mock: mã luôn là 123456)');
        }
        return { message: 'Nếu email đã được đăng ký, mã xác nhận đã được gửi tới email đó. Mã có hiệu lực 10 phút.' };
      }),

    confirmPasswordReset: (body) =>
      call(() => {
        const user = state.users.find((candidate) => candidate.email === body.email.trim().toLowerCase());
        if (body.newPassword.length < 8 || body.newPassword.length > 72) fail('PASSWORD_POLICY');
        const code = user ? (state.resetCodes ?? []).find((candidate) => candidate.userId === user.id) : undefined;
        if (!user || !code) return fail('RESET_CODE_INVALID');
        if (code.used || Date.now() >= code.expiresAt) fail('RESET_CODE_EXPIRED');
        if (code.failed >= 5) fail('RESET_CODE_LOCKED');
        if (body.code !== MOCK_OTP) {
          code.failed += 1;
          return fail('RESET_CODE_INVALID');
        }
        checkNewPassword(user, body.newPassword);
        code.used = true;
        user.password = body.newPassword;
        user.mustChangePassword = false;
        audit(user.id, 'KHOI_PHUC_MAT_KHAU', user.id, 'Đặt lại mật khẩu bằng mã gửi qua email');
      }),

    changeVehicleType: (vehicleId, vehicleTypeCode) =>
      call(() => {
        const vehicle = ownedIdleVehicle(requireRole('SINH_VIEN'), vehicleId);
        if (!VEHICLE_TYPES.some((type) => type.code === vehicleTypeCode)) {
          invalid([{ field: 'vehicleTypeCode', code: 'NOT_FOUND', message: 'Loại xe không tồn tại' }]);
        }
        vehicle.vehicleTypeCode = vehicleTypeCode;
        return toVehicle(vehicle);
      }),

    deactivateVehicle: (vehicleId) =>
      call(() => {
        const vehicle = ownedIdleVehicle(requireRole('SINH_VIEN'), vehicleId);
        vehicle.status = 'NGUNG_SU_DUNG';
        vehicle.deactivatedAt = toVietnamIso(new Date());
        return toVehicle(vehicle);
      }),

    resetStudentPassword: (rawCode) =>
      call(() => {
        const manager = requireRole('QUAN_LY');
        const student = state.users.find((user) => user.studentCode === rawCode.trim().toUpperCase());
        if (!student) return fail('STUDENT_NOT_FOUND');
        return issueTemporary(manager.id, student);
      }),

    resetGuardPassword: (guardId) =>
      call(() => {
        const manager = requireRole('QUAN_LY');
        const guard = state.users.find((user) => user.id === guardId && user.role === 'BAO_VE');
        if (!guard) return fail('GUARD_NOT_FOUND');
        return issueTemporary(manager.id, guard);
      }),

    updateGuard: (guardId, body) =>
      call(() => {
        const manager = requireRole('QUAN_LY');
        const guard = state.users.find((user) => user.id === guardId && user.role === 'BAO_VE');
        if (!guard) return fail('GUARD_NOT_FOUND');
        const fullName = body.fullName.trim();
        const phone = body.phone.replace(/[\s.-]/g, '');
        const fields: FieldViolation[] = [];
        if (!fullName || fullName.length > 100) fields.push({ field: 'fullName', code: 'LENGTH', message: 'Họ tên tối đa 100 ký tự' });
        if (!PHONE.test(phone)) {
          fields.push({ field: 'phone', code: 'PATTERN', message: 'Số điện thoại gồm 10–11 chữ số, bắt đầu bằng 0' });
        }
        invalid(fields);
        guard.fullName = fullName;
        guard.phone = phone;
        audit(manager.id, 'CAP_NHAT_BAO_VE', guard.id, `Cập nhật thông tin của ${guard.username}`);
        return { id: guard.id, username: guard.username, fullName, phone };
      }),

    auditLogs: (query) =>
      call(() => {
        requireRole('QUAN_LY');
        const limit = query.limit ?? 20;
        const nameOf = (id: number | null) => state.users.find((user) => user.id === id)?.fullName ?? null;
        const rows = [...(state.auditLogs ?? [])]
          .filter((entry) => query.targetUserId === undefined || entry.targetUserId === query.targetUserId)
          .filter((entry) => query.before === undefined || entry.id < query.before)
          .sort((a, b) => b.id - a.id);
        const items: AuditItem[] = rows.slice(0, limit).map((entry) => ({
          ...entry,
          actorName: nameOf(entry.actorId) ?? '',
          targetName: nameOf(entry.targetUserId),
        }));
        const hasMore = rows.length > limit;
        return { items, hasMore, nextBefore: hasMore ? (items[items.length - 1]?.id ?? null) : null };
      }),

    // ===== Đợt 2 =====

    parkedVehicles: (rawFragment) =>
      call(() => {
        requireRole('BAO_VE');
        const fragment = normalizePlate(rawFragment ?? '').replace(/[^A-Z0-9]/g, '');
        const now = Date.now();
        const all: ParkedVehicleItem[] = state.sessions
          .filter((session) => session.exitedAt === null)
          .sort((a, b) => a.enteredAt.localeCompare(b.enteredAt))
          .flatMap((session) => {
            const vehicle = state.vehicles.find((candidate) => candidate.id === session.vehicleId);
            const owner = vehicle ? state.users.find((user) => user.id === vehicle.userId) : undefined;
            if (!vehicle || !owner) return [];
            const minutesParked = Math.max(0, Math.floor((now - sinceMs(session.enteredAt)) / 60_000));
            return [{
              sessionId: session.id, plateNumber: vehicle.plateNumber, plateDisplay: displayPlate(vehicle.plateNumber),
              vehicleTypeCode: vehicle.vehicleTypeCode, studentCode: owner.studentCode ?? '', fullName: owner.fullName,
              enteredAt: session.enteredAt, entryGuardName: nameOf(session.entryGuardId ?? 0) ?? '—',
              minutesParked, overdue: minutesParked > 24 * 60,
            }];
          });
        return {
          total: all.length,
          overdue: all.filter((item) => item.overdue).length,
          items: fragment ? all.filter((item) => item.plateNumber.includes(fragment)) : all,
        };
      }),

    myGateActivity: () =>
      call(() => {
        const guard = requireRole('BAO_VE');
        const shift = (state.shifts ?? []).find((candidate) => candidate.guardId === guard.id && candidate.endedAt === null);
        const from = shift?.startedAt ?? toVietnamIso(new Date(Date.now() - 12 * 3600_000));
        const plateOf = (vehicleId: number) => state.vehicles.find((vehicle) => vehicle.id === vehicleId);
        const items = state.sessions
          .flatMap((session) => {
            const vehicle = plateOf(session.vehicleId);
            const owner = vehicle ? state.users.find((user) => user.id === vehicle.userId) : undefined;
            if (!vehicle || !owner) return [];
            const base = {
              sessionId: session.id, plateNumber: vehicle.plateNumber, plateDisplay: displayPlate(vehicle.plateNumber),
              vehicleTypeCode: vehicle.vehicleTypeCode, studentCode: owner.studentCode ?? '', fullName: owner.fullName,
            };
            const entries = [];
            if (session.entryGuardId === guard.id && sinceMs(session.enteredAt) >= sinceMs(from)) {
              entries.push({ ...base, direction: 'VAO' as const, at: session.enteredAt });
            }
            if (session.exitGuardId === guard.id && session.exitedAt && sinceMs(session.exitedAt) >= sinceMs(from)) {
              entries.push({ ...base, direction: 'RA' as const, at: session.exitedAt });
            }
            return entries;
          })
          .sort((a, b) => b.at.localeCompare(a.at));
        const summary = summarize(guard.id, from);
        return { from, inShift: shift !== undefined, checkIns: summary.checkIns, checkOuts: summary.checkOuts, items };
      }),

    currentShift: () =>
      call(() => {
        const guard = requireRole('BAO_VE');
        const shifts = state.shifts ?? [];
        const current = shifts.find((shift) => shift.guardId === guard.id && shift.endedAt === null) ?? null;
        return {
          current: current ? toShiftView(current) : null,
          liveSummary: current ? summarize(guard.id, current.startedAt) : null,
          pendingHandovers: shifts
            .filter((shift) => shift.endedAt !== null && shift.receivedBy === null && shift.guardId !== guard.id)
            .sort((a, b) => b.id - a.id)
            .map(toShiftView),
          openIncidents: openIncidents().slice(-10).reverse().map(toIncident),
        };
      }),

    startShift: (acknowledged, openingCash) =>
      call(() => {
        const guard = requireRole('BAO_VE');
        checkCash(openingCash);
        state.shifts = state.shifts ?? [];
        const open = state.shifts.find((shift) => shift.guardId === guard.id && shift.endedAt === null);
        if (open) return toShiftView(open);
        const pending = state.shifts.filter((shift) => shift.endedAt !== null && shift.receivedBy === null && shift.guardId !== guard.id);
        const same = pending.length === acknowledged.length && pending.every((shift) => acknowledged.includes(shift.id));
        if (!same) fail('HANDOVER_PENDING');
        const now = toVietnamIso(new Date());
        pending.forEach((shift) => {
          shift.receivedBy = guard.id;
          shift.receivedAt = now;
        });
        const shift: MockShift = {
          id: nextId(), guardId: guard.id, startedAt: now, endedAt: null, summary: null,
          handoverNote: null, receivedBy: null, openingCash,
          expectedOpeningCash: pending.length ? pending.reduce((sum, item) => sum + (item.closingCash ?? 0), 0) : null,
          closingCash: null, receivedAt: null,
        };
        state.shifts.push(shift);
        return toShiftView(shift);
      }),

    endShift: (note, closingCash) =>
      call(() => {
        const guard = requireRole('BAO_VE');
        checkCash(closingCash);
        const shift = (state.shifts ?? []).find((candidate) => candidate.guardId === guard.id && candidate.endedAt === null);
        if (!shift) return fail('NO_OPEN_SHIFT');
        shift.summary = summarize(guard.id, shift.startedAt);
        shift.endedAt = toVietnamIso(new Date());
        shift.handoverNote = note.trim() || null;
        shift.closingCash = closingCash;
        return toShiftView(shift);
      }),

    myShiftHistory: () =>
      call(() => {
        const guard = requireRole('BAO_VE');
        return (state.shifts ?? []).filter((shift) => shift.guardId === guard.id).sort((a, b) => b.id - a.id).map(toShiftView);
      }),

    guardTimesheet: (from, to, guardId) =>
      call(() => {
        requireRole('QUAN_LY');
        const start = new Date(`${from}T00:00:00+07:00`).getTime();
        const end = new Date(`${to}T00:00:00+07:00`).getTime() + 24 * 3600_000;
        const shifts = (state.shifts ?? [])
          .filter((shift) => sinceMs(shift.startedAt) >= start && sinceMs(shift.startedAt) < end)
          .filter((shift) => guardId === undefined || shift.guardId === guardId)
          .sort((a, b) => a.startedAt.localeCompare(b.startedAt))
          .map(toShiftView);
        const byGuard = new Map<number, ShiftView[]>();
        shifts.forEach((shift) => byGuard.set(shift.guardId, [...(byGuard.get(shift.guardId) ?? []), shift]));
        const totals = [...byGuard.entries()].map(([id, rows]) => ({
          guardId: id, guardName: nameOf(id), shifts: rows.length,
          workedMinutes: rows.reduce((sum, row) => sum + row.workedMinutes, 0),
          checkIns: rows.reduce((sum, row) => sum + (row.summary?.checkIns ?? 0), 0),
          checkOuts: rows.reduce((sum, row) => sum + (row.summary?.checkOuts ?? 0), 0),
          cashMismatches: rows.filter((row) => row.openingDiscrepancy !== null && row.openingDiscrepancy !== 0).length,
        }));
        return { from, to, totals, shifts };
      }),

    reportIncident: (body) =>
      call(() => {
        const guard = requireRole('BAO_VE');
        const description = body.description.trim();
        const plate = normalizePlate(body.plateNumber ?? '');
        const fields: FieldViolation[] = [];
        if (!description || description.length > 1000) {
          fields.push({ field: 'description', code: 'LENGTH', message: 'Vui lòng mô tả sự cố (tối đa 1000 ký tự)' });
        }
        if (plate && !PLATE.test(plate)) fields.push({ field: 'plateNumber', code: 'PATTERN', message: 'Biển số không hợp lệ' });
        invalid(fields);
        if (body.photoBase64 && !/^image\/(jpeg|png|webp)$/.test(body.photoContentType ?? '')) fail('PHOTO_INVALID');
        const incident: MockIncident = {
          id: nextId(), reporterId: guard.id, type: body.type, plateNumber: plate || null, description,
          status: 'MOI', createdAt: toVietnamIso(new Date()), resolution: null,
          photo: body.photoBase64
            ? (body.photoBase64.startsWith('data:') ? body.photoBase64 : `data:${body.photoContentType};base64,${body.photoBase64}`)
            : null,
        };
        state.incidents = [...(state.incidents ?? []), incident];
        return toIncident(incident);
      }),

    myIncidents: () =>
      call(() => {
        const guard = requireRole('BAO_VE');
        return (state.incidents ?? []).filter((incident) => incident.reporterId === guard.id).sort((a, b) => b.id - a.id).map(toIncident);
      }),

    // ===== Đợt 3 =====

    ownerOverview: () =>
      call(() => {
        const owner = requireRole('CHU_TAI_KHOAN');
        const today = toVietnamIso(new Date()).slice(0, 10);
        const month = dailyRevenue(`${today.slice(0, 8)}01`, today);
        const todayRow = month.find((day) => day.date === today);
        const balance = balanceOf(owner.id);
        return {
          ownerName: owner.fullName, balance, pendingPoints: pendingPoints(),
          available: Math.max(0, balance - pendingPoints()),
          revenueToday: todayRow?.revenue ?? 0, paymentsToday: todayRow?.payments ?? 0,
          revenueMonth: month.reduce((sum, day) => sum + day.revenue, 0), cashHeld: cashHeld(), cashPerPoint: CASH_PER_POINT,
        };
      }),

    ownerRevenue: (from, to) =>
      call(() => {
        requireRole('CHU_TAI_KHOAN');
        if (to < from) fail('VALIDATION_ERROR');
        const days = dailyRevenue(from, to);
        const total = (key: keyof Omit<RevenueDay, 'date'>) => days.reduce((sum, day) => sum + day[key], 0);
        return {
          from, to, days, totalPayments: total('payments'), totalRevenue: total('revenue'),
          totalAdjustments: total('adjustments'), totalCashIn: total('cashIn'), totalCashOut: total('cashOut'),
        };
      }),

    myWithdrawalRequests: () =>
      call(() => {
        requireRole('CHU_TAI_KHOAN');
        return [...(state.withdrawals ?? [])].sort((a, b) => b.id - a.id).map(toWithdrawal);
      }),

    createWithdrawalRequest: (points) =>
      call(() => {
        const owner = requireRole('CHU_TAI_KHOAN');
        if (!Number.isSafeInteger(points) || points <= 0) fail('INVALID_AMOUNT');
        if (pendingPoints() + points > balanceOf(owner.id)) fail('WITHDRAWAL_EXCEEDS_AVAILABLE');      // R15
        const request: MockWithdrawal = {
          id: nextId(), points, status: 'CHO_XU_LY', createdAt: toVietnamIso(new Date()), cancelledAt: null,
          processedBy: null, processedAt: null, rejectReason: null, transactionId: null, confirmedAt: null,
          complaintNote: null, complainedAt: null, resolutionNote: null, resolvedBy: null, resolvedAt: null,
        };
        state.withdrawals = [...(state.withdrawals ?? []), request];
        return toWithdrawal(request);
      }),

    cancelWithdrawalRequest: (id) =>
      call(() => {
        requireRole('CHU_TAI_KHOAN');
        const request = withdrawalOf(id);
        moveWithdrawal(request, ['CHO_XU_LY'], 'DA_HUY');
        request.cancelledAt = toVietnamIso(new Date());
        return toWithdrawal(request);
      }),

    confirmWithdrawalRequest: (id) =>
      call(() => {
        requireRole('CHU_TAI_KHOAN');
        const request = withdrawalOf(id);
        moveWithdrawal(request, ['DA_TRA'], 'DA_XAC_NHAN');
        request.confirmedAt = toVietnamIso(new Date());
        return toWithdrawal(request);
      }),

    complainWithdrawalRequest: (id, note) =>
      call(() => {
        requireRole('CHU_TAI_KHOAN');
        const text = requireNote(note);
        const request = withdrawalOf(id);
        moveWithdrawal(request, ['DA_TRA'], 'KHIEU_NAI');
        request.complaintNote = text;
        request.complainedAt = toVietnamIso(new Date());
        return toWithdrawal(request);
      }),

    withdrawalQueue: (status) =>
      call(() => {
        requireRole('QUAN_LY');
        const owner = state.users.find((user) => user.id === OWNER_ID);
        return {
          ownerName: owner?.fullName ?? '', cashHeld: cashHeld(),
          items: [...(state.withdrawals ?? [])].filter((w) => !status || w.status === status).sort((a, b) => b.id - a.id)
            .map(toWithdrawal),
        };
      }),

    payWithdrawalRequest: (id) =>
      call(() => {
        const manager = requireRole('QUAN_LY');
        const request = withdrawalOf(id);
        if (['DA_TRA', 'DA_XAC_NHAN', 'KHIEU_NAI'].includes(request.status)) return { request: toWithdrawal(request), replayed: true };
        if (request.points * CASH_PER_POINT > cashHeld()) fail('CASH_FUND_INSUFFICIENT');                // R16
        if (balanceOf(OWNER_ID) < request.points) fail('INSUFFICIENT_POINTS');
        moveWithdrawal(request, ['CHO_XU_LY'], 'DA_TRA');
        const transaction = record('RUT_DIEM', [[OWNER_ID, -request.points]], new Date());
        request.transactionId = transaction.id;
        request.processedBy = manager.id;
        request.processedAt = transaction.createdAt;
        return { request: toWithdrawal(request), replayed: false };
      }),

    rejectWithdrawalRequest: (id, note) =>
      call(() => {
        const manager = requireRole('QUAN_LY');
        const text = requireNote(note);
        const request = withdrawalOf(id);
        moveWithdrawal(request, ['CHO_XU_LY'], 'TU_CHOI');
        request.rejectReason = text;
        request.processedBy = manager.id;
        request.processedAt = toVietnamIso(new Date());
        return toWithdrawal(request);
      }),

    resolveWithdrawalRequest: (id, note) =>
      call(() => {
        const manager = requireRole('QUAN_LY');
        const text = requireNote(note);
        const request = withdrawalOf(id);
        moveWithdrawal(request, ['KHIEU_NAI'], 'DA_XAC_NHAN');
        request.resolutionNote = text;
        request.resolvedBy = manager.id;
        request.resolvedAt = toVietnamIso(new Date());
        return toWithdrawal(request);
      }),

    adjustPoints: (body, key) =>
      call(() => {
        const manager = requireRole('QUAN_LY');
        const reason = requireNote(body.reason, 'reason');
        const student = state.users.find((user) => user.studentCode === body.studentCode.trim().toUpperCase());
        if (!student) return fail('STUDENT_NOT_FOUND');
        if (!Number.isSafeInteger(body.points) || body.points <= 0) fail('INVALID_AMOUNT');
        if (body.originalTransactionId && !state.transactions.some((t) => t.id === body.originalTransactionId)) {
          fail('TRANSACTION_NOT_FOUND');
        }
        const refund = body.direction === 'HOAN_DIEM';
        const [from, to] = refund ? [OWNER_ID, student.id] : [student.id, OWNER_ID];
        return idempotent(key, `DIEU_CHINH|${manager.id}|${JSON.stringify(body)}`, () => {
          if (balanceOf(from) < body.points) fail('INSUFFICIENT_POINTS');
          const transaction = record('DIEU_CHINH', [[from, -body.points], [to, body.points]], new Date());
          return {
            transactionId: transaction.id, studentCode: student.studentCode ?? '', studentName: student.fullName,
            direction: body.direction, points: body.points, reason, originalTransactionId: body.originalTransactionId ?? null,
            createdAt: transaction.createdAt,
          };
        });
      }),

    incidentPhotoUrl: (incidentId) => (state.incidents ?? []).find((incident) => incident.id === incidentId)?.photo ?? '',
  };
}
