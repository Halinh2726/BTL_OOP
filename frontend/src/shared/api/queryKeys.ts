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
};
