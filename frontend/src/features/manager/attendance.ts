import type { AttendanceStatus } from '../../shared/types/api';

/** Màu huy hiệu chuyên cần — dùng chung cho màn phân ca (quản lý) và lịch trực (bảo vệ). */
export const ATTENDANCE_TONE: Record<AttendanceStatus, 'gray' | 'amber' | 'green' | 'red' | 'blue'> = {
  SAP_TOI: 'gray',
  CHUA_NHAN_CA: 'blue',
  DUNG_GIO: 'green',
  DI_MUON: 'amber',
  VANG: 'red',
};

/** "2026-11-20" ± n ngày (lịch, không phụ thuộc múi giờ máy). */
export function addDays(date: string, days: number): string {
  return new Date(Date.parse(`${date}T12:00:00Z`) + days * 86_400_000).toISOString().slice(0, 10);
}
