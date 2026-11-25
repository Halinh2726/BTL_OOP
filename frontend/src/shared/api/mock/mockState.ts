import type {
  ComplaintStatus,
  ComplaintType,
  CameraReviewStatus,
  ShiftSlot,
  AuditAction,
  GuardStatus,
  IncidentStatus,
  IncidentType,
  Role,
  ShiftSummary,
  TransactionType,
  VehicleStatus,
  WithdrawalStatus,
} from '../../types/api';

/** Dữ liệu giả lập — chỉ dùng khi VITE_API_MODE=mock, để bấm thử giao diện khi không có backend. */
export interface MockUser {
  id: number;
  username: string;
  password: string;
  fullName: string;
  role: Role;
  status: GuardStatus;
  studentCode?: string;
  phone?: string;
  /** Đợt 1 */
  email?: string;
  mustChangePassword?: boolean;
  passwordChangedAt?: string;
}

export interface MockVehicle {
  id: number;
  userId: number;
  plateNumber: string;
  vehicleTypeCode: string;
  registeredAt: string;
  /** Đợt 1 — thiếu = đang dùng (dữ liệu cũ trong sessionStorage). */
  status?: VehicleStatus;
  deactivatedAt?: string | null;
}

/** Đợt 1 — mã OTP khôi phục mật khẩu: chế độ mock luôn dùng mã {@link MOCK_OTP}. */
export interface MockResetCode {
  userId: number;
  expiresAt: number;
  failed: number;
  used: boolean;
}

export interface MockAuditEntry {
  id: number;
  actorId: number;
  action: AuditAction;
  targetUserId: number | null;
  detail: string;
  createdAt: string;
}

export const MOCK_OTP = '123456';

/** Đợt 3 — yêu cầu rút điểm của chủ bãi xe. */
export interface MockWithdrawal {
  id: number;
  points: number;
  status: WithdrawalStatus;
  createdAt: string;
  cancelledAt: string | null;
  processedBy: number | null;
  processedAt: string | null;
  rejectReason: string | null;
  transactionId: number | null;
  confirmedAt: string | null;
  complaintNote: string | null;
  complainedAt: string | null;
  resolutionNote: string | null;
  resolvedBy: number | null;
  resolvedAt: string | null;
}

export interface MockParkingSession {
  id: number;
  vehicleId: number;
  enteredAt: string;
  exitedAt: string | null;
  /** Đợt 2 — bảo vệ cho vào / cho ra (thiếu với dữ liệu cũ). */
  entryGuardId?: number;
  exitGuardId?: number | null;
}

/** Đợt 2 — ca trực / biên bản bàn giao. */
export interface MockShift {
  id: number;
  guardId: number;
  startedAt: string;
  endedAt: string | null;
  summary: ShiftSummary | null;
  handoverNote: string | null;
  receivedBy: number | null;
  /** Thiếu với dữ liệu cũ trong sessionStorage -> coi như 0 / null. */
  openingCash?: number;
  expectedOpeningCash?: number | null;
  closingCash?: number | null;
  receivedAt?: string | null;
}

export interface MockIncident {
  id: number;
  reporterId: number;
  type: IncidentType;
  plateNumber: string | null;
  description: string;
  status: IncidentStatus;
  createdAt: string;
  /** Ảnh dạng data URL để đặt thẳng vào thẻ img. */
  photo: string | null;
  resolution: string | null;
  /** Đợt 4b — quản lý xử lý. */
  handledBy?: number | null;
  handledAt?: string | null;
}

/** Đợt 4b — khiếu nại của sinh viên. */
export interface MockComplaint {
  id: number;
  studentId: number;
  type: ComplaintType;
  sessionId: number | null;
  description: string;
  status: ComplaintStatus;
  createdAt: string;
  photo: string | null;
  cancelledAt: string | null;
  handledBy: number | null;
  handledAt: string | null;
  resolution: string | null;
  refundTransactionId: number | null;
  proposedPoints: number | null;
  proposedBy: number | null;
  proposedAt: string | null;
  decidedBy: number | null;
  decidedAt: string | null;
  decisionNote: string | null;
  compensationTransactionId: number | null;
  cameraRequested?: boolean;
  cameraStatus?: CameraReviewStatus;
  incidentArea?: string | null;
  incidentTimeRange?: string | null;
  cameraNote?: string | null;
  cameraReviewerId?: number | null;
  cameraReviewedAt?: string | null;
  cameraPhoto?: string | null;
}

/** Đợt 5 — vé tháng (month "yyyy-MM"). */
export interface MockPass {
  id: number;
  studentId: number;
  vehicleId: number;
  month: string;
  price: number;
  transactionId: number;
}

/** Đợt 5 — thông báo trong hộp thư của một người dùng. */
export interface MockNotification {
  id: number;
  userId: number;
  kind: string;
  title: string;
  body: string;
  createdAt: string;
  read: boolean;
}

/** Đợt 4b — một ca đã xếp. */
export interface MockAssignment {
  id: number;
  date: string;
  slot: ShiftSlot;
  guardId: number;
}

/** Một bút toán trên ví của một người dùng. Giao dịch thanh toán có hai bút toán: sinh viên (−) và chủ tài khoản (+). */
export interface MockEntry {
  userId: number;
  change: number;
  balanceBefore: number;
  balanceAfter: number;
}

export interface MockTransaction {
  id: number;
  type: TransactionType;
  createdAt: string;
  entries: MockEntry[];
  /** Đợt 4a — thiếu với dữ liệu cũ: suy ra theo loại (bảo vệ thu phí, quản lý làm phần còn lại). */
  performedBy?: number;
  sessionId?: number;
  reason?: string;
  originalTransactionId?: number | null;
}

/** Đợt 4a — biên bản đối soát cuối ngày. */
export interface MockReconciliation {
  date: string;
  cashIn: number;
  cashOut: number;
  expectedCash: number;
  countedCash: number;
  pointsBalanced: boolean;
  note: string | null;
  closedBy: number;
  closedAt: string;
}

export interface MockIdempotencyRecord {
  fingerprint: string;
  result: unknown;
}

export interface MockState {
  nextId: number;
  users: MockUser[];
  wallets: Record<number, number>;
  vehicles: MockVehicle[];
  sessions: MockParkingSession[];
  transactions: MockTransaction[];
  idempotency: Record<string, MockIdempotencyRecord>;
  currentUserId: number | null;
  loginFailures: Record<string, number[]>;
  /** Đợt 1 — có thể thiếu với dữ liệu cũ trong sessionStorage. */
  resetCodes?: MockResetCode[];
  auditLogs?: MockAuditEntry[];
  bicycleSeq?: number;
  /** Đợt 2 */
  shifts?: MockShift[];
  incidents?: MockIncident[];
  /** Đợt 3 */
  withdrawals?: MockWithdrawal[];
  /** Đợt 4a */
  reconciliations?: MockReconciliation[];
  /** Đợt 4b */
  complaints?: MockComplaint[];
  assignments?: MockAssignment[];
  /** Đợt 5 */
  passes?: MockPass[];
  notifications?: MockNotification[];
  passPrices?: Record<string, number>;
  feeSchedules?: Record<string, Array<{ upToMinutes: number; feePoints: number }>>;
}

const HOUR = 60 * 60 * 1000;
const VN_OFFSET = 7 * HOUR;

/** Giờ Việt Nam kèm múi giờ, giống backend: "2026-10-05T18:30:00+07:00". */
export function toVietnamIso(date: Date): string {
  return `${new Date(date.getTime() + VN_OFFSET).toISOString().slice(0, 19)}+07:00`;
}

export const MANAGER_ID = 1;
export const OWNER_ID = 2;
export const STUDENT_ID = 3;
export const GUARD_ID = 4;
export const STUDENT2_ID = 5;

export function seedState(now = new Date()): MockState {
  const at = (hoursAgo: number) => toVietnamIso(new Date(now.getTime() - hoursAgo * HOUR));
  return {
    nextId: 100,
    users: [
      { id: MANAGER_ID, username: 'quanly01', password: 'demo1234', fullName: 'Phạm Văn Quản', role: 'QUAN_LY', status: 'HOAT_DONG' },
      { id: OWNER_ID, username: 'chutk01', password: 'demo1234', fullName: 'Lê Văn C', role: 'CHU_TAI_KHOAN', status: 'HOAT_DONG' },
      {
        id: STUDENT_ID, username: 'sv01', password: 'demo1234', fullName: 'Nguyễn Văn A', role: 'SINH_VIEN',
        status: 'HOAT_DONG', studentCode: 'B21DCCN001', email: 'sv01@ptit.edu.vn',
      },
      {
        id: GUARD_ID, username: 'baove01', password: 'demo1234', fullName: 'Trần Văn Bảo', role: 'BAO_VE',
        status: 'HOAT_DONG', phone: '0912345678',
      },
      {
        id: STUDENT2_ID, username: 'sv02', password: 'demo1234', fullName: 'Trần Thị B', role: 'SINH_VIEN',
        status: 'HOAT_DONG', studentCode: 'B21DCCN002',
      },
    ],
    wallets: { [OWNER_ID]: 4000, [STUDENT_ID]: 44000, [STUDENT2_ID]: 2000 },
    vehicles: [
      { id: 1, userId: STUDENT_ID, plateNumber: '29A12345', vehicleTypeCode: 'XE_MAY', registeredAt: at(24 * 7) },
      { id: 2, userId: STUDENT2_ID, plateNumber: '30B67890', vehicleTypeCode: 'XE_MAY_DIEN', registeredAt: at(24 * 6) },
      { id: 3, userId: STUDENT2_ID, plateNumber: '29T67021', vehicleTypeCode: 'XE_MAY', registeredAt: at(24 * 4) },
    ],
    sessions: [
      { id: 1, vehicleId: 1, enteredAt: at(24 * 3 + 9), exitedAt: at(24 * 3 + 2), entryGuardId: GUARD_ID, exitGuardId: GUARD_ID },
      { id: 2, vehicleId: 1, enteredAt: at(24 * 2 + 9), exitedAt: at(24 * 2 + 3), entryGuardId: GUARD_ID, exitGuardId: GUARD_ID },
      { id: 3, vehicleId: 3, enteredAt: at(30), exitedAt: null, entryGuardId: GUARD_ID, exitGuardId: null },
    ],
    transactions: [
      { id: 1, type: 'DOI_DIEM', createdAt: at(24 * 5), entries: [{ userId: STUDENT_ID, change: 50000, balanceBefore: 0, balanceAfter: 50000 }] },
      { id: 2, type: 'DOI_DIEM', createdAt: at(24 * 5 - 1), entries: [{ userId: STUDENT2_ID, change: 2000, balanceBefore: 0, balanceAfter: 2000 }] },
      {
        id: 3, type: 'THANH_TOAN', createdAt: at(24 * 3 + 2), sessionId: 1, performedBy: GUARD_ID, entries: [
          { userId: STUDENT_ID, change: -3000, balanceBefore: 50000, balanceAfter: 47000 },
          { userId: OWNER_ID, change: 3000, balanceBefore: 0, balanceAfter: 3000 },
        ],
      },
      {
        id: 4, type: 'THANH_TOAN', createdAt: at(24 * 2 + 3), sessionId: 2, performedBy: GUARD_ID, entries: [
          { userId: STUDENT_ID, change: -3000, balanceBefore: 47000, balanceAfter: 44000 },
          { userId: OWNER_ID, change: 3000, balanceBefore: 3000, balanceAfter: 6000 },
        ],
      },
      { id: 5, type: 'RUT_DIEM', createdAt: at(24), entries: [{ userId: OWNER_ID, change: -2000, balanceBefore: 6000, balanceAfter: 4000 }] },
    ],
    idempotency: {},
    currentUserId: null,
    loginFailures: {},
    resetCodes: [],
    auditLogs: [],
    bicycleSeq: 0,
    shifts: [],
    incidents: [],
    withdrawals: [],
    reconciliations: [],
    complaints: [],
    assignments: [],
    passes: [],
    notifications: [],
    passPrices: { XE_MAY: 100_000, XE_MAY_DIEN: 80_000, XE_DAP: 50_000 },
    feeSchedules: {
      XE_MAY: [
        { upToMinutes: 1080, feePoints: 3000 },
        { upToMinutes: 1320, feePoints: 5000 },
        { upToMinutes: 1440, feePoints: 15000 },
      ],
      XE_MAY_DIEN: [
        { upToMinutes: 1080, feePoints: 3000 },
        { upToMinutes: 1320, feePoints: 5000 },
        { upToMinutes: 1440, feePoints: 15000 },
      ],
      XE_DAP: [
        { upToMinutes: 1080, feePoints: 3000 },
        { upToMinutes: 1320, feePoints: 5000 },
        { upToMinutes: 1440, feePoints: 15000 },
      ],
    },
  };
}
