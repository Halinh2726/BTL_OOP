import type { GuardStatus, Role, TransactionType } from '../types/api';

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
  CHU_TAI_KHOAN: 'Chủ tài khoản',
};

export const TRANSACTION_LABELS: Record<TransactionType, string> = {
  DOI_DIEM: 'Đổi điểm',
  THANH_TOAN: 'Thanh toán phí gửi xe',
  RUT_DIEM: 'Rút điểm',
};

export const GUARD_STATUS_LABELS: Record<GuardStatus, string> = {
  HOAT_DONG: 'Hoạt động',
  BI_KHOA: 'Bị khóa',
};

/** Lời nhắn cho reason của màn hình xe vào/xe ra. */
export const REASON_LABELS: Record<string, string> = {
  PLATE_NOT_FOUND: 'Biển số chưa được đăng ký',
  SESSION_ALREADY_OPEN: 'Xe đang có lượt gửi chưa kết thúc',
  NO_OPEN_SESSION: 'Xe không có lượt gửi đang mở',
  INSUFFICIENT_POINTS: 'Không đủ điểm',
};

/** Bỏ khoảng trắng, dấu chấm, gạch ngang và viết hoa — cùng quy tắc chuẩn hóa biển số với backend. */
export function normalizePlate(raw: string): string {
  return raw.replace(/[\s.-]/g, '').toUpperCase();
}
