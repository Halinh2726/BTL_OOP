import type { GuardStatus, Role, TransactionType } from '../../types/api';

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
}

export interface MockVehicle {
  id: number;
  userId: number;
  plateNumber: string;
  vehicleTypeCode: string;
  registeredAt: string;
}

export interface MockParkingSession {
  id: number;
  vehicleId: number;
  enteredAt: string;
  exitedAt: string | null;
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
}

const HOUR = 60 * 60 * 1000;
const VN_OFFSET = 7 * HOUR;

/** Giờ Việt Nam kèm múi giờ, giống backend: "2026-10-05T18:30:00+07:00". */
export function toVietnamIso(date: Date): string {
  return `${new Date(date.getTime() + VN_OFFSET).toISOString().slice(0, 19)}+07:00`;
}

/** "HH:mm" theo giờ Việt Nam. */
export function vietnamClock(date: Date): string {
  return new Date(date.getTime() + VN_OFFSET).toISOString().slice(11, 16);
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
      { id: MANAGER_ID, username: 'quanly01', password: 'Quanly@123', fullName: 'Phạm Văn Quản', role: 'QUAN_LY', status: 'HOAT_DONG' },
      { id: OWNER_ID, username: 'chutk01', password: 'Chutk@123', fullName: 'Lê Văn C', role: 'CHU_TAI_KHOAN', status: 'HOAT_DONG' },
      {
        id: STUDENT_ID, username: 'sv01', password: 'MatKhau@123', fullName: 'Nguyễn Văn A', role: 'SINH_VIEN',
        status: 'HOAT_DONG', studentCode: 'B21DCCN001',
      },
      {
        id: GUARD_ID, username: 'baove01', password: 'Baove@123', fullName: 'Trần Văn Bảo', role: 'BAO_VE',
        status: 'HOAT_DONG', phone: '0912345678',
      },
      {
        id: STUDENT2_ID, username: 'sv02', password: 'MatKhau@123', fullName: 'Trần Thị B', role: 'SINH_VIEN',
        status: 'HOAT_DONG', studentCode: 'B21DCCN002',
      },
    ],
    wallets: { [OWNER_ID]: 4000, [STUDENT_ID]: 44000, [STUDENT2_ID]: 2000 },
    vehicles: [
      { id: 1, userId: STUDENT_ID, plateNumber: '29A12345', vehicleTypeCode: 'XE_MAY', registeredAt: at(24 * 7) },
      { id: 2, userId: STUDENT2_ID, plateNumber: '30B67890', vehicleTypeCode: 'XE_DAP_DIEN', registeredAt: at(24 * 6) },
    ],
    sessions: [
      { id: 1, vehicleId: 1, enteredAt: at(24 * 3 + 9), exitedAt: at(24 * 3 + 2) },
      { id: 2, vehicleId: 1, enteredAt: at(24 * 2 + 9), exitedAt: at(24 * 2 + 3) },
    ],
    transactions: [
      { id: 1, type: 'DOI_DIEM', createdAt: at(24 * 5), entries: [{ userId: STUDENT_ID, change: 50000, balanceBefore: 0, balanceAfter: 50000 }] },
      { id: 2, type: 'DOI_DIEM', createdAt: at(24 * 5 - 1), entries: [{ userId: STUDENT2_ID, change: 2000, balanceBefore: 0, balanceAfter: 2000 }] },
      {
        id: 3, type: 'THANH_TOAN', createdAt: at(24 * 3 + 2), entries: [
          { userId: STUDENT_ID, change: -3000, balanceBefore: 50000, balanceAfter: 47000 },
          { userId: OWNER_ID, change: 3000, balanceBefore: 0, balanceAfter: 3000 },
        ],
      },
      {
        id: 4, type: 'THANH_TOAN', createdAt: at(24 * 2 + 3), entries: [
          { userId: STUDENT_ID, change: -3000, balanceBefore: 47000, balanceAfter: 44000 },
          { userId: OWNER_ID, change: 3000, balanceBefore: 3000, balanceAfter: 6000 },
        ],
      },
      { id: 5, type: 'RUT_DIEM', createdAt: at(24), entries: [{ userId: OWNER_ID, change: -2000, balanceBefore: 6000, balanceAfter: 4000 }] },
    ],
    idempotency: {},
    currentUserId: null,
    loginFailures: {},
  };
}
