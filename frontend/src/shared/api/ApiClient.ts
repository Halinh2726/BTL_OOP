import type {
  AdjustRequest,
  AdjustResult,
  AuditPage,
  ChangePasswordRequest,
  CheckInPreview,
  CheckInResult,
  CheckOutPreview,
  CheckOutResult,
  ConfirmPasswordResetRequest,
  CreateGuardRequest,
  DepositRequest,
  DepositResult,
  ExchangeRate,
  GateActivityResponse,
  GuardTimesheet,
  IncidentItem,
  ParkedVehiclesResponse,
  ReportIncidentRequest,
  ShiftOverview,
  ShiftView,
  FeeRule,
  Guard,
  GuardStatus,
  LoginRequest,
  MeResponse,
  OwnerOverview,
  OwnerWallet,
  PayWithdrawalResult,
  RevenueReport,
  PlateSuggestion,
  Profile,
  RegisteredStudent,
  RegisterStudentRequest,
  RegisterVehicleRequest,
  StudentSummary,
  TemporaryPasswordResult,
  TransactionPage,
  TransactionQuery,
  UpdateGuardRequest,
  UpdateProfileRequest,
  Vehicle,
  VehicleType,
  Wallet,
  WithdrawalQueue,
  WithdrawalStatus,
  WithdrawalView,
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
  /** Gợi ý biển số chứa `fragment` (≥ 2 ký tự); parkedOnly = chỉ xe đang trong bãi. */
  suggestPlates(fragment: string, parkedOnly: boolean): Promise<PlateSuggestion[]>;
  previewCheckIn(plate: string): Promise<CheckInPreview>;
  checkIn(plateNumber: string): Promise<CheckInResult>;
  previewCheckOut(plate: string): Promise<CheckOutPreview>;
  checkOut(sessionId: number, idempotencyKey: string): Promise<Idempotent<CheckOutResult>>;

  // Quản lý
  lookupStudent(studentCode: string): Promise<StudentSummary>;
  deposit(body: DepositRequest, idempotencyKey: string): Promise<Idempotent<DepositResult>>;
  ownerWallet(): Promise<OwnerWallet>;
  guards(): Promise<Guard[]>;
  createGuard(body: CreateGuardRequest): Promise<Guard>;
  changeGuardStatus(guardId: number, status: GuardStatus): Promise<Guard>;

  // Đợt 1 — tài khoản của tôi (mọi vai trò)
  myProfile(): Promise<Profile>;
  updateMyProfile(body: UpdateProfileRequest): Promise<Profile>;
  changePassword(body: ChangePasswordRequest): Promise<void>;
  /** Quên mật khẩu bước 1 — luôn "thành công", không cho biết email có tồn tại không. */
  requestPasswordReset(email: string): Promise<{ message: string }>;
  confirmPasswordReset(body: ConfirmPasswordResetRequest): Promise<void>;

  // Đợt 1 — xe của tôi
  changeVehicleType(vehicleId: number, vehicleTypeCode: string): Promise<Vehicle>;
  deactivateVehicle(vehicleId: number): Promise<Vehicle>;

  // Đợt 1 — quản lý tài khoản
  resetStudentPassword(studentCode: string): Promise<TemporaryPasswordResult>;
  resetGuardPassword(guardId: number): Promise<TemporaryPasswordResult>;
  updateGuard(guardId: number, body: UpdateGuardRequest): Promise<Pick<Guard, 'id' | 'username' | 'fullName' | 'phone'>>;
  auditLogs(query: { before?: number; limit?: number; targetUserId?: number }): Promise<AuditPage>;

  // Đợt 2 — bảo vệ
  parkedVehicles(fragment?: string): Promise<ParkedVehiclesResponse>;
  myGateActivity(): Promise<GateActivityResponse>;
  currentShift(): Promise<ShiftOverview>;
  /** acknowledgedHandoverIds: các biên bản bàn giao bảo vệ đã xem trên màn hình. */
  /** openingCash: tiền mặt (đồng) đang cầm lúc nhận ca. */
  startShift(acknowledgedHandoverIds: number[], openingCash: number): Promise<ShiftView>;
  /** closingCash: tiền mặt (đồng) bàn giao lại cho ca sau. */
  endShift(note: string, closingCash: number): Promise<ShiftView>;
  /** Lịch sử trực ca của tôi (chấm công cá nhân), mới nhất trước. */
  myShiftHistory(): Promise<ShiftView[]>;
  /** Quản lý: chấm công bảo vệ trong khoảng ngày "yyyy-MM-dd". */
  guardTimesheet(from: string, to: string, guardId?: number): Promise<GuardTimesheet>;
  reportIncident(body: ReportIncidentRequest): Promise<IncidentItem>;
  myIncidents(): Promise<IncidentItem[]>;
  /** Địa chỉ ảnh sự cố để đặt vào thẻ img (cookie phiên tự đi kèm vì cùng origin). */
  incidentPhotoUrl(incidentId: number): string;

  // Đợt 3 — chủ bãi xe
  ownerOverview(): Promise<OwnerOverview>;
  ownerRevenue(from: string, to: string): Promise<RevenueReport>;
  myWithdrawalRequests(): Promise<WithdrawalView[]>;
  createWithdrawalRequest(points: number): Promise<WithdrawalView>;
  cancelWithdrawalRequest(id: number): Promise<WithdrawalView>;
  confirmWithdrawalRequest(id: number): Promise<WithdrawalView>;
  complainWithdrawalRequest(id: number, note: string): Promise<WithdrawalView>;

  // Đợt 3 — quản lý (thay cho rút điểm tự do đã bỏ)
  withdrawalQueue(status?: WithdrawalStatus): Promise<WithdrawalQueue>;
  payWithdrawalRequest(id: number): Promise<PayWithdrawalResult>;
  rejectWithdrawalRequest(id: number, note: string): Promise<WithdrawalView>;
  resolveWithdrawalRequest(id: number, note: string): Promise<WithdrawalView>;
  adjustPoints(body: AdjustRequest, idempotencyKey: string): Promise<Idempotent<AdjustResult>>;
}
