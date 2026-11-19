import type { TransactionType } from '../types/api';

/** Khóa cache của TanStack Query — gom một chỗ để invalidate đúng dữ liệu sau mỗi thao tác. */
export const queryKeys = {
  me: ['me'] as const,
  vehicleTypes: ['vehicle-types'] as const,
  feeRules: ['fee-rules'] as const,
  exchangeRate: ['exchange-rate'] as const,
  myVehicles: ['me', 'vehicles'] as const,
  myWallet: ['me', 'wallet'] as const,
  myTransactionsAll: ['me', 'transactions'] as const,
  myTransactions: (type: TransactionType | undefined) => ['me', 'transactions', type ?? 'ALL'] as const,
  student: (studentCode: string) => ['manager', 'student', studentCode] as const,
  ownerWallet: ['manager', 'owner-wallet'] as const,
  guards: ['manager', 'guards'] as const,
  myProfile: ['me', 'profile'] as const,
  auditLogs: ['manager', 'audit-logs'] as const,
  parkedVehicles: (fragment: string) => ['guard', 'parked', fragment] as const,
  parkedVehiclesAll: ['guard', 'parked'] as const,
  myGateActivity: ['guard', 'activity'] as const,
  currentShift: ['guard', 'shift'] as const,
  myIncidents: ['guard', 'incidents'] as const,
  myShiftHistory: ['guard', 'shift-history'] as const,
  ownerOverview: ['owner', 'overview'] as const,
  ownerRevenue: (from: string, to: string) => ['owner', 'revenue', from, to] as const,
  myWithdrawals: ['owner', 'withdrawals'] as const,
  withdrawalQueue: (status: string | undefined) => ['manager', 'withdrawals', status ?? 'ALL'] as const,
  withdrawalQueueAll: ['manager', 'withdrawals'] as const,
  guardTimesheet: (from: string, to: string, guardId: number | undefined) =>
    ['manager', 'timesheet', from, to, guardId ?? 'ALL'] as const,
};
