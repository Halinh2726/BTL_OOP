/**
 * Kiểu dữ liệu của API (Đặc tả API). Tên trường khớp ĐÚNG JSON backend trả về — không đổi tên ở đây.
 * Thời gian là chuỗi ISO 8601 kèm múi giờ +07:00.
 */

export type Role = 'SINH_VIEN' | 'BAO_VE' | 'QUAN_LY' | 'CHU_TAI_KHOAN';

export interface SessionUser {
  id: number;
  username: string;
  fullName: string;
  role: Role;
}

/** POST /auth/login, GET /me */
export interface MeResponse {
  user: SessionUser;
  permissions: string[];
  /** Đợt 1: đang dùng mật khẩu tạm do quản lý cấp — phải đổi trước khi làm việc khác. */
  mustChangePassword: boolean;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterStudentRequest {
  studentCode: string;
  fullName: string;
  username: string;
  password: string;
}

export interface RegisteredStudent {
  id: number;
  username: string;
  fullName: string;
  studentCode: string;
}

export interface VehicleType {
  code: string;
  name: string;
}

export interface FeeRule {
  startTime: string;
  feePoints: number;
}

export interface ExchangeRate {
  cashPerPoint: number;
}

export type VehicleStatus = 'DANG_DUNG' | 'NGUNG_SU_DUNG';

export interface Vehicle {
  id: number;
  /** Dạng chuẩn hóa (chỉ chữ và số) — dùng cho mã QR, tra cứu. */
  plateNumber: string;
  /** Dạng hiển thị theo quy định: "29A-123.45", "XD-00001". */
  plateDisplay: string;
  vehicleTypeCode: string;
  status: VehicleStatus;
  registeredAt: string;
  deactivatedAt: string | null;
}

export interface RegisterVehicleRequest {
  /** Để trống với xe đạp không có biển: bãi tự cấp mã XD-xxxxx. */
  plateNumber: string;
  vehicleTypeCode: string;
}

// ===== Đợt 1: tài khoản =====

/** GET/PATCH /me/profile */
export interface Profile {
  userId: number;
  username: string;
  role: Role;
  fullName: string;
  email: string | null;
  phone: string | null;
  studentCode: string | null;
  phoneEditable: boolean;
  phoneRequired: boolean;
}

export interface UpdateProfileRequest {
  fullName: string;
  email: string;
  phone: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface ConfirmPasswordResetRequest {
  email: string;
  code: string;
  newPassword: string;
}

/** POST /manager/students/{mssv}/password-reset, /manager/guards/{id}/password-reset — chỉ hiện MỘT lần. */
export interface TemporaryPasswordResult {
  userId: number;
  username: string;
  fullName: string;
  temporaryPassword: string;
}

export interface UpdateGuardRequest {
  fullName: string;
  phone: string;
}

export type AuditAction =
  | 'CAP_NHAT_THONG_TIN'
  | 'DOI_MAT_KHAU'
  | 'YEU_CAU_KHOI_PHUC_MAT_KHAU'
  | 'KHOI_PHUC_MAT_KHAU'
  | 'CAP_MAT_KHAU_TAM'
  | 'TAO_BAO_VE'
  | 'CAP_NHAT_BAO_VE'
  | 'KHOA_TAI_KHOAN'
  | 'MO_KHOA_TAI_KHOAN';

export interface AuditItem {
  id: number;
  actorId: number;
  actorName: string;
  action: AuditAction;
  targetUserId: number | null;
  targetName: string | null;
  detail: string;
  createdAt: string;
}

export interface AuditPage {
  items: AuditItem[];
  hasMore: boolean;
  nextBefore: number | null;
}

export interface Wallet {
  balance: number;
}

export type TransactionType = 'DOI_DIEM' | 'THANH_TOAN' | 'RUT_DIEM' | 'DIEU_CHINH';

/** Một dòng lịch sử, nhìn từ góc ví của người đang xem: change có dấu. */
export interface TransactionItem {
  id: number;
  type: TransactionType;
  change: number;
  balanceBefore: number;
  balanceAfter: number;
  createdAt: string;
}

export interface TransactionPage {
  items: TransactionItem[];
  hasMore: boolean;
  nextBefore: number | null;
}

export interface TransactionQuery {
  type?: TransactionType;
  limit?: number;
  before?: number;
}

export type CheckInReason = 'PLATE_NOT_FOUND' | 'SESSION_ALREADY_OPEN' | 'INSUFFICIENT_POINTS';

/** GET /guard/check-in/preview — thông tin chủ xe null khi biển số chưa đăng ký. */
export interface CheckInPreview {
  plateNumber: string | null;
  vehicleTypeCode: string | null;
  studentCode: string | null;
  fullName: string | null;
  balance: number | null;
  minFee: number;
  allowed: boolean;
  reason: CheckInReason | null;
}

export interface CheckInResult {
  sessionId: number;
  plateNumber: string;
  enteredAt: string;
}

export type CheckOutReason = 'PLATE_NOT_FOUND' | 'NO_OPEN_SESSION' | 'INSUFFICIENT_POINTS';

/** GET /guard/check-out/preview — trường nào không có (chưa đăng ký, không có lượt mở) thì null. */
export interface CheckOutPreview {
  sessionId: number | null;
  plateNumber: string | null;
  vehicleTypeCode: string | null;
  studentCode: string | null;
  fullName: string | null;
  enteredAt: string | null;
  balance: number | null;
  fee: number | null;
  allowed: boolean;
  reason: CheckOutReason | null;
}

export interface CheckOutResult {
  transactionId: number;
  sessionId: number;
  fee: number;
  exitedAt: string;
  /** Số điểm còn lại của SINH VIÊN. */
  balanceAfter: number;
}

/** GET /guard/plates — gợi ý biển số, KHÔNG có thông tin chủ xe. */
export interface PlateSuggestion {
  plateNumber: string;
  vehicleTypeCode: string;
  /** Xe đang có lượt gửi chưa ra. */
  parked: boolean;
}

export interface StudentSummary {
  studentCode: string;
  fullName: string;
  balance: number;
}

export interface DepositRequest {
  studentCode: string;
  cashAmount: number;
}

export interface DepositResult {
  transactionId: number;
  studentCode: string;
  cashAmount: number;
  cashPerPoint: number;
  points: number;
  balanceBefore: number;
  balanceAfter: number;
  createdAt: string;
}

export interface OwnerWallet {
  fullName: string;
  balance: number;
}

export type GuardStatus = 'HOAT_DONG' | 'BI_KHOA';

export interface Guard {
  id: number;
  username: string;
  fullName: string;
  phone: string;
  status: GuardStatus;
}

export interface CreateGuardRequest {
  fullName: string;
  phone: string;
  username: string;
  password: string;
}

/** Lỗi của một trường: code là REQUIRED, PATTERN, LENGTH, RANGE hoặc NOT_FOUND. */
export interface FieldViolation {
  field: string;
  code: string;
  message: string;
}

/** Cấu trúc lỗi thống nhất (Đặc tả API mục 5). */
export interface ApiErrorBody {
  code: string;
  message: string;
  status: number;
  traceId: string;
  fields?: FieldViolation[];
}

// ===== Đợt 2: bảo vệ — không có trường điểm hay tiền nào (R06) =====

export interface ParkedVehicleItem {
  sessionId: number;
  plateNumber: string;
  plateDisplay: string;
  vehicleTypeCode: string;
  studentCode: string;
  fullName: string;
  enteredAt: string;
  entryGuardName: string;
  minutesParked: number;
  /** Gửi quá 24 giờ. */
  overdue: boolean;
}

export interface ParkedVehiclesResponse {
  total: number;
  overdue: number;
  items: ParkedVehicleItem[];
}

export interface GateActivityItem {
  sessionId: number;
  plateNumber: string;
  plateDisplay: string;
  vehicleTypeCode: string;
  studentCode: string;
  fullName: string;
  direction: 'VAO' | 'RA';
  at: string;
}

export interface GateActivityResponse {
  from: string;
  inShift: boolean;
  checkIns: number;
  checkOuts: number;
  items: GateActivityItem[];
}

export interface ShiftSummary {
  checkIns: number;
  checkOuts: number;
  vehiclesInLot: number;
  openIncidents: number;
}

export interface ShiftView {
  id: number;
  guardId: number;
  guardName: string | null;
  startedAt: string;
  endedAt: string | null;
  /** Chấm công: số phút đã trực (ca đang trực tính tới hiện tại). */
  workedMinutes: number;
  summary: ShiftSummary | null;
  handoverNote: string | null;
  /** Tiền mặt (đồng) bảo vệ KHAI đang cầm lúc nhận ca. */
  openingCash: number;
  /** Tổng tiền các ca trước bàn giao lại; null nếu không nhận biên bản nào. */
  expectedOpeningCash: number | null;
  /** openingCash − expectedOpeningCash; khác 0 là khai lệch. */
  openingDiscrepancy: number | null;
  /** Tiền mặt (đồng) bảo vệ khai bàn giao lúc kết thúc ca; null khi đang trực. */
  closingCash: number | null;
  receivedByName: string | null;
  receivedAt: string | null;
}

/** Chấm công: tổng hợp một bảo vệ trong khoảng ngày. */
export interface GuardTotals {
  guardId: number;
  guardName: string | null;
  shifts: number;
  workedMinutes: number;
  checkIns: number;
  checkOuts: number;
  /** Số ca khai tiền nhận ca lệch với tiền ca trước bàn giao. */
  cashMismatches: number;
}

export interface GuardTimesheet {
  from: string;
  to: string;
  totals: GuardTotals[];
  shifts: ShiftView[];
}

export type IncidentType = 'XE_GUI_QUA_LAU' | 'XE_CHUA_DANG_KY' | 'XE_HU_HONG' | 'KHAC';
export type IncidentStatus = 'MOI' | 'DANG_XU_LY' | 'DA_XU_LY' | 'TU_CHOI';

export interface IncidentItem {
  id: number;
  type: IncidentType;
  plateNumber: string | null;
  plateDisplay: string | null;
  description: string;
  status: IncidentStatus;
  createdAt: string;
  reporterName: string | null;
  hasPhoto: boolean;
  resolution: string | null;
}

export interface ShiftOverview {
  current: ShiftView | null;
  liveSummary: ShiftSummary | null;
  pendingHandovers: ShiftView[];
  openIncidents: IncidentItem[];
}

export interface ReportIncidentRequest {
  type: IncidentType;
  plateNumber: string;
  description: string;
  photoContentType?: string;
  /** Ảnh đã thu nhỏ, dạng Base64 (có thể kèm tiền tố data:). */
  photoBase64?: string;
}

// ===== Đợt 3: rút điểm theo yêu cầu, điều chỉnh, tài chính chủ bãi xe =====

export type WithdrawalStatus = 'CHO_XU_LY' | 'DA_TRA' | 'DA_XAC_NHAN' | 'TU_CHOI' | 'DA_HUY' | 'KHIEU_NAI';

export interface WithdrawalView {
  id: number;
  points: number;
  cashPerPoint: number;
  /** Số tiền mặt sẽ trả / đã trả (đồng). */
  cashAmount: number;
  method: 'TIEN_MAT';
  status: WithdrawalStatus;
  createdAt: string;
  cancelledAt: string | null;
  processedByName: string | null;
  processedAt: string | null;
  rejectReason: string | null;
  transactionId: number | null;
  confirmedAt: string | null;
  complaintNote: string | null;
  complainedAt: string | null;
  resolutionNote: string | null;
  resolvedByName: string | null;
  resolvedAt: string | null;
}

/** GET /manager/withdrawal-requests */
export interface WithdrawalQueue {
  ownerName: string;
  /** Quỹ tiền mặt quản lý đang giữ (R13) — trả tiền không được vượt (R16). */
  cashHeld: number;
  items: WithdrawalView[];
}

export interface PayWithdrawalResult {
  request: WithdrawalView;
  /** true: yêu cầu đã được trả từ trước, lần này không trừ điểm (R17). */
  replayed: boolean;
}

/** GET /owner/overview */
export interface OwnerOverview {
  ownerName: string;
  balance: number;
  /** Điểm đang nằm trong các yêu cầu rút chờ xử lý. */
  pendingPoints: number;
  /** Điểm còn tạo được yêu cầu rút (R15). */
  available: number;
  revenueToday: number;
  paymentsToday: number;
  revenueMonth: number;
  cashHeld: number;
  cashPerPoint: number;
}

export interface RevenueDay {
  date: string;
  payments: number;
  revenue: number;
  /** Điểm điều chỉnh ròng vào ví chủ bãi (âm: đã hoàn cho sinh viên). */
  adjustments: number;
  /** Tiền mặt vào hệ thống (đổi điểm). */
  cashIn: number;
  /** Tiền mặt ra khỏi hệ thống (rút điểm). */
  cashOut: number;
}

export interface RevenueReport {
  from: string;
  to: string;
  days: RevenueDay[];
  totalPayments: number;
  totalRevenue: number;
  totalAdjustments: number;
  totalCashIn: number;
  totalCashOut: number;
}

export type AdjustmentDirection = 'HOAN_DIEM' | 'THU_HOI';

export interface AdjustRequest {
  studentCode: string;
  direction: AdjustmentDirection;
  points: number;
  reason: string;
  originalTransactionId?: number;
}

export interface AdjustResult {
  transactionId: number;
  studentCode: string;
  studentName: string;
  direction: AdjustmentDirection;
  points: number;
  reason: string;
  originalTransactionId: number | null;
  createdAt: string;
}

// ===== Đợt 4a — giám sát & đối soát của quản lý =====

/** Một bên của giao dịch (ví nguồn / ví đích). */
export interface LedgerParty {
  fullName: string;
  studentCode: string | null;
  role: Role;
}

/** Một dòng sổ giao dịch toàn hệ thống. `from` null với đổi điểm, `to` null với rút điểm. */
export interface LedgerItem {
  id: number;
  type: TransactionType;
  points: number;
  cashAmount: number | null;
  from: LedgerParty | null;
  to: LedgerParty | null;
  performerName: string;
  performerRole: Role;
  createdAt: string;
  plateNumber: string | null;
  reason: string | null;
  originalTransactionId: number | null;
}

export interface LedgerPage {
  items: LedgerItem[];
  hasMore: boolean;
  nextBefore: number | null;
}

/** Lọc sổ giao dịch; ngày dạng "yyyy-mm-dd" (giờ Việt Nam). */
export interface LedgerQuery {
  from?: string;
  to?: string;
  type?: TransactionType;
  studentCode?: string;
  before?: number;
  limit?: number;
}

export interface StudentSearchRow {
  userId: number;
  studentCode: string;
  fullName: string;
  status: GuardStatus;
  balance: number;
  activeVehicles: number;
  parkedNow: boolean;
}

export interface StudentProfileView {
  student: {
    userId: number;
    studentCode: string;
    fullName: string;
    email: string | null;
    phone: string | null;
    status: GuardStatus;
    createdAt: string;
    balance: number;
  };
  vehicles: Array<{
    id: number;
    plateNumber: string;
    vehicleTypeName: string;
    status: VehicleStatus;
    registeredAt: string;
    parkedNow: boolean;
  }>;
  recentSessions: Array<{ id: number; plateNumber: string; enteredAt: string; exitedAt: string | null; fee: number | null }>;
  recentTransactions: LedgerItem[];
}

export type ReconciliationOutcome = 'KHOP' | 'THUA' | 'THIEU';

export interface ClosedReconciliation {
  date: string;
  cashIn: number;
  cashOut: number;
  expectedCash: number;
  countedCash: number;
  /** Dương: thừa; âm: thiếu so với sổ sách. */
  discrepancy: number;
  outcome: ReconciliationOutcome;
  pointsBalanced: boolean;
  note: string | null;
  closedByName: string;
  closedAt: string;
}

/** Sổ sách tiền mặt của một ngày (R13) và kết luận bảo toàn điểm (R12). */
export interface ReconciliationDay {
  date: string;
  openingCash: number;
  cashIn: number;
  cashOut: number;
  expectedCash: number;
  deposits: number;
  withdrawals: number;
  payments: number;
  revenue: number;
  pointsBalanced: boolean;
  closed: ClosedReconciliation | null;
}

export interface CloseReconciliationRequest {
  date: string;
  countedCash: number;
  note?: string;
}

export interface TrafficHour {
  hour: number;
  averageEntries: number;
  averageExits: number;
  todayEntries: number;
  todayExits: number;
}

export interface TrafficReport {
  from: string;
  to: string;
  days: number;
  hours: TrafficHour[];
  totalEntries: number;
  averageEntriesPerDay: number;
  peakHours: number[];
  parkedNow: number;
}
