import type {
  CheckInPreview,
  CheckInResult,
  CheckOutPreview,
  CheckOutResult,
  CreateGuardRequest,
  DepositRequest,
  DepositResult,
  ExchangeRate,
  FeeRule,
  Guard,
  GuardStatus,
  LoginRequest,
  MeResponse,
  OwnerWallet,
  RegisteredStudent,
  RegisterStudentRequest,
  RegisterVehicleRequest,
  StudentSummary,
  TransactionPage,
  TransactionQuery,
  Vehicle,
  VehicleType,
  Wallet,
  WithdrawalResult,
} from '../types/api';

/** Kết quả của API có Idempotency-Key: replayed = true khi backend trả lại giao dịch đã tạo trước đó. */
export interface Idempotent<T> {
  data: T;
  replayed: boolean;
}

/**
 * Hợp đồng gọi backend (23 API của Đặc tả API). Giao diện chỉ phụ thuộc interface này;
 * có hai cài đặt thay thế được cho nhau: gọi HTTP thật và dữ liệu giả lập.
 * Mọi lỗi được ném ra dưới dạng {@link import('./ApiError').ApiError}.
 */
export interface ApiClient {
  // Xác thực & dùng chung
  login(body: LoginRequest): Promise<MeResponse>;
  logout(): Promise<void>;
  me(): Promise<MeResponse>;
  registerStudent(body: RegisterStudentRequest): Promise<RegisteredStudent>;
  vehicleTypes(): Promise<VehicleType[]>;
  feeRules(): Promise<FeeRule[]>;
  exchangeRate(): Promise<ExchangeRate>;

  // Sinh viên, chủ tài khoản
  myVehicles(): Promise<Vehicle[]>;
  registerVehicle(body: RegisterVehicleRequest): Promise<Vehicle>;
  myWallet(): Promise<Wallet>;
  myTransactions(query: TransactionQuery): Promise<TransactionPage>;

  // Bảo vệ
  previewCheckIn(plate: string): Promise<CheckInPreview>;
  checkIn(plateNumber: string): Promise<CheckInResult>;
  previewCheckOut(plate: string): Promise<CheckOutPreview>;
  checkOut(sessionId: number, idempotencyKey: string): Promise<Idempotent<CheckOutResult>>;

  // Quản lý
  lookupStudent(studentCode: string): Promise<StudentSummary>;
  deposit(body: DepositRequest, idempotencyKey: string): Promise<Idempotent<DepositResult>>;
  ownerWallet(): Promise<OwnerWallet>;
  withdraw(points: number, idempotencyKey: string): Promise<Idempotent<WithdrawalResult>>;
  guards(): Promise<Guard[]>;
  createGuard(body: CreateGuardRequest): Promise<Guard>;
  changeGuardStatus(guardId: number, status: GuardStatus): Promise<Guard>;
}
