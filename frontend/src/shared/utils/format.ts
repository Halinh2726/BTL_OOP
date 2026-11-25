import type {
  AttendanceStatus,
  AuditAction,
  CameraReviewStatus,
  ComplaintStatus,
  ComplaintType,
  GuardStatus,
  IncidentStatus,
  IncidentType,
  ReconciliationOutcome,
  Role,
  ShiftSlot,
  TransactionType,
  VehicleStatus,
  WithdrawalStatus,
} from '../types/api';

const NUMBER = new Intl.NumberFormat('vi-VN');

/** 50000 -> "50.000" */
export function formatNumber(value: number): string {
  return NUMBER.format(value);
}

export function formatPoints(value: number): string {
  return `${formatNumber(value)} điểm`;
}

export function formatCash(value: number): string {
  return `${formatNumber(value)} đ`;
}

/** Điểm có dấu: +3.000 / −3.000 */
export function formatSignedPoints(value: number): string {
  if (value > 0) return `+${formatNumber(value)}`;
  if (value < 0) return `−${formatNumber(-value)}`;
  return '0';
}

/**
 * "2026-10-05T18:30:00+07:00" -> "05/10/2026 18:30". Lấy thẳng giờ trong chuỗi (đã là giờ Việt Nam),
 * không đổi theo múi giờ của máy đang xem.
 */
export function formatDateTime(iso: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso);
  if (!match) return iso;
  const [, year, month, day, hour, minute] = match;
  return `${day}/${month}/${year} ${hour}:${minute}`;
}

export const ROLE_LABELS: Record<Role, string> = {
  SINH_VIEN: 'Sinh viên',
  BAO_VE: 'Bảo vệ',
  QUAN_LY: 'Quản lý',
  CHU_TAI_KHOAN: 'Chủ bãi xe',
};

export const INCIDENT_TYPE_LABELS: Record<IncidentType, string> = {
  XE_GUI_QUA_LAU: 'Xe gửi quá lâu',
  XE_CHUA_DANG_KY: 'Xe chưa đăng ký đỗ trong bãi',
  XE_HU_HONG: 'Xe bị hư hỏng',
  MAT_VE: 'Khách mất vé',
  KHAC: 'Khác',
};

export const INCIDENT_STATUS_LABELS: Record<IncidentStatus, string> = {
  MOI: 'Mới',
  DANG_XU_LY: 'Đang xử lý',
  DA_XU_LY: 'Đã xử lý',
  TU_CHOI: 'Từ chối',
};

/** 1500 phút -> "1 ngày 1 giờ"; 95 -> "1 giờ 35 phút". */
export function formatDuration(minutes: number): string {
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  const mins = minutes % 60;
  if (days > 0) return `${days} ngày ${hours} giờ`;
  if (hours > 0) return `${hours} giờ ${mins} phút`;
  return `${mins} phút`;
}

/** Chỉ giờ:phút của chuỗi ISO giờ Việt Nam. */
export function formatTime(iso: string): string {
  const match = /T(\d{2}):(\d{2})/.exec(iso);
  return match ? `${match[1]}:${match[2]}` : iso;
}

export const VEHICLE_STATUS_LABELS: Record<VehicleStatus, string> = {
  DANG_DUNG: 'Đang dùng',
  NGUNG_SU_DUNG: 'Ngừng sử dụng',
};

export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  CAP_NHAT_THONG_TIN: 'Cập nhật thông tin cá nhân',
  DOI_MAT_KHAU: 'Đổi mật khẩu',
  YEU_CAU_KHOI_PHUC_MAT_KHAU: 'Yêu cầu mã khôi phục mật khẩu',
  KHOI_PHUC_MAT_KHAU: 'Đặt lại mật khẩu bằng mã email',
  CAP_MAT_KHAU_TAM: 'Cấp mật khẩu tạm',
  TAO_BAO_VE: 'Tạo tài khoản bảo vệ',
  CAP_NHAT_BAO_VE: 'Cập nhật thông tin bảo vệ',
  KHOA_TAI_KHOAN: 'Khóa tài khoản',
  MO_KHOA_TAI_KHOAN: 'Mở khóa tài khoản',
};

export const TRANSACTION_LABELS: Record<TransactionType, string> = {
  DOI_DIEM: 'Nạp điểm',
  THANH_TOAN: 'Thanh toán phí gửi xe',
  RUT_DIEM: 'Rút điểm',
  DIEU_CHINH: 'Điều chỉnh',
  MUA_VE: 'Mua vé tháng',
};

/** "2026-11-30" -> "30/11/2026"; "2026-11" -> "11/2026" */
export function formatDay(value: string): string {
  return value.split('-').reverse().join('/');
}

export const WITHDRAWAL_STATUS_LABELS: Record<WithdrawalStatus, string> = {
  CHO_XU_LY: 'Chờ xử lý',
  DA_TRA: 'Đã trả — chờ chủ bãi xác nhận',
  DA_XAC_NHAN: 'Hoàn tất',
  TU_CHOI: 'Bị từ chối',
  DA_HUY: 'Đã hủy',
  KHIEU_NAI: 'Khiếu nại chưa nhận đủ',
};

export const RECONCILIATION_OUTCOME_LABELS: Record<ReconciliationOutcome, string> = {
  KHOP: 'Khớp sổ sách',
  THUA: 'Thừa tiền',
  THIEU: 'Thiếu tiền',
};

/** 7 -> "07:00–08:00" */
export function hourRange(hour: number): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${pad(hour)}:00–${pad((hour + 1) % 24)}:00`;
}

export const COMPLAINT_TYPE_LABELS: Record<ComplaintType, string> = {
  THU_PHI_SAI: 'Thu phí sai',
  XE_HU_HONG: 'Xe bị hư hỏng trong bãi',
  MAT_DO: 'Mất đồ trong bãi',
  TRICH_XUAT_CAMERA: 'Trích xuất camera xem lịch sử',
  DICH_VU_KHAC: 'Dịch vụ khác',
  KHAC: 'Khác',
};

export const CAMERA_STATUS_LABELS: Record<CameraReviewStatus, string> = {
  KHONG_YEU_CAU: 'Không yêu cầu trích xuất',
  CHO_TRICH_XUAT: 'Chờ trích xuất camera',
  DANG_TRICH_XUAT: 'Đang trích xuất camera',
  DA_TRICH_XUAT: 'Đã trích xuất camera',
  KHONG_CO_DU_LIEU: 'Không có dữ liệu camera',
};

export const NOTIFICATION_KIND_META: Record<string, { label: string; tone: 'blue' | 'green' | 'amber' | 'red' | 'slate' }> = {
  XE_VAO: { label: 'Xe vào bãi', tone: 'blue' },
  XE_RA: { label: 'Xe ra & trừ điểm', tone: 'green' },
  SO_DU_THAP: { label: 'Sắp hết điểm', tone: 'amber' },
  VE_THANG: { label: 'Vé tháng', tone: 'blue' },
  KHIEU_NAI: { label: 'Khiếu nại', tone: 'slate' },
  CAMERA: { label: 'Trích xuất camera', tone: 'blue' },
  BOI_THUONG: { label: 'Bồi thường', tone: 'green' },
  BAO_MAT: { label: 'Bảo mật tài khoản', tone: 'red' },
  SU_CO: { label: 'Sự cố bãi xe', tone: 'amber' },
  PHAN_CA: { label: 'Phân ca trực', tone: 'slate' },
  RUT_DIEM: { label: 'Rút điểm', tone: 'slate' },
};

export const COMPLAINT_STATUS_LABELS: Record<ComplaintStatus, string> = {
  MOI: 'Mới gửi',
  DANG_XU_LY: 'Đang xử lý',
  CHO_DUYET_BOI_THUONG: 'Chờ chủ bãi duyệt bồi thường',
  DA_XU_LY: 'Đã xử lý',
  TU_CHOI: 'Bị từ chối',
  DA_HUY: 'Đã hủy',
  DA_BOI_THUONG: 'Đã bồi thường',
  KHONG_BOI_THUONG: 'Không bồi thường',
};

export const SHIFT_SLOT_LABELS: Record<ShiftSlot, string> = {
  SANG: 'Ca sáng 06:00–14:00',
  CHIEU: 'Ca chiều 14:00–22:00',
  DEM: 'Ca đêm 22:00–06:00',
};

export const ATTENDANCE_LABELS: Record<AttendanceStatus, string> = {
  SAP_TOI: 'Sắp tới',
  CHUA_NHAN_CA: 'Chưa nhận ca',
  DUNG_GIO: 'Đúng giờ',
  DI_MUON: 'Đi muộn',
  VANG: 'Vắng',
};

export const GUARD_STATUS_LABELS: Record<GuardStatus, string> = {
  HOAT_DONG: 'Hoạt động',
  BI_KHOA: 'Bị khóa',
};

/** Lời nhắn cho reason của màn hình xe vào/xe ra. */
export const REASON_LABELS: Record<string, string> = {
  PLATE_NOT_FOUND: 'Biển số chưa được đăng ký',
  SESSION_ALREADY_OPEN: 'Xe đang có lượt gửi chưa kết thúc',
  GUEST_SESSION_ALREADY_OPEN: 'Xe đang có vé khách. Hãy kết thúc lượt khách trước.',
  STUDENT_ALREADY_PARKED: 'Sinh viên đã có xe đang gửi. Hãy lấy xe đó ra trước',
  NO_OPEN_SESSION: 'Xe không có lượt gửi đang mở',
  INSUFFICIENT_POINTS: 'Không đủ điểm',
  CHECKOUT_CURFEW: 'Đã quá giờ khóa lấy xe trong ngày (từ 22:00)',
};

/** Bỏ khoảng trắng, dấu chấm, gạch ngang và viết hoa — cùng quy tắc chuẩn hóa biển số với backend. */
export function normalizePlate(raw: string): string {
  return raw.replace(/[\s.-]/g, '').toUpperCase();
}

/** Đợt 1 — biển số theo quy định SAU chuẩn hóa (khớp PlateNumber.STANDARD_PATTERN của backend). */
export const MOTORCYCLE_PLATE = /^[0-9]{2}[ABCDEFGHKLMNPSTUVXYZ](?:[ABCDEFGHKLMNPSTUVXYZ0-9]|[ABCDEFGHKLMNPSTUVXYZ][0-9])[0-9]{5}$/;
export const ELECTRIC_MOTORCYCLE_PLATE = /^[0-9]{2}MĐ[0-9]{5}$/;
export const STANDARD_PLATE = /^(?:[0-9]{2}[ABCDEFGHKLMNPSTUVXYZ](?:[ABCDEFGHKLMNPSTUVXYZ0-9]|[ABCDEFGHKLMNPSTUVXYZ][0-9])[0-9]{5}|[0-9]{2}MĐ[0-9]{5}|XD[0-9]{5})$/;

export function isPlateValidForVehicleType(plate: string, vehicleTypeCode: string): boolean {
  if (vehicleTypeCode === 'XE_MAY') return MOTORCYCLE_PLATE.test(plate);
  if (vehicleTypeCode === 'XE_MAY_DIEN') return ELECTRIC_MOTORCYCLE_PLATE.test(plate);
  return false;
}

/**
 * Dạng hiển thị có dấu, cùng quy tắc PlateNumber.display() của backend:
 * "29A1514" → "29A-1514", "36S412345" → "36S4-12345", "29MĐ12345" → "29MĐ-123.45", "XD00001" → "XD-00001".
 */
export function formatPlate(normalized: string): string {
  const bicycle = /^XD([0-9]{5})$/.exec(normalized);
  if (bicycle) return `XD-${bicycle[1]}`;
  const electric = /^([0-9]{2}MĐ)([0-9]{5})$/.exec(normalized);
  if (electric) return `${electric[1]}-${electric[2]?.slice(0, 3)}.${electric[2]?.slice(3)}`;
  const parts = /^([0-9]{2}[A-ZĐ][A-Z0-9Đ]?)([0-9]{4,6})$/.exec(normalized);
  if (!parts) return normalized;
  let series = parts[1] ?? '';
  let digits = parts[2] ?? '';
  if (digits.length === 6) {
    series += digits.charAt(0);
    digits = digits.slice(1);
  }
  return digits.length === 5 ? `${series}-${digits.slice(0, 3)}.${digits.slice(3)}` : `${series}-${digits}`;
}
