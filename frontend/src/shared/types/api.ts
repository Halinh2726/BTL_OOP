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

export interface Vehicle {
  id: number;
  plateNumber: string;
  vehicleTypeCode: string;
  registeredAt: string;
}

export interface RegisterVehicleRequest {
  plateNumber: string;
  vehicleTypeCode: string;
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
