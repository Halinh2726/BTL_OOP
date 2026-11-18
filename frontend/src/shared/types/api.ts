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

export type TransactionType = 'DOI_DIEM' | 'THANH_TOAN' | 'RUT_DIEM';

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

export interface WithdrawalResult {
  transactionId: number;
  points: number;
  cashPerPoint: number;
  /** Số tiền quản lý trả cho chủ tài khoản. */
  cashAmount: number;
  balanceBefore: number;
  balanceAfter: number;
  createdAt: string;
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
