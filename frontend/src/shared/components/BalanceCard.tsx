import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { formatNumber } from '../utils/format';
import { QueryError } from './QueryError';
import { Spinner } from './Spinner';
import { DepositPointsDialog } from '../../features/student/DepositPointsDialog';
import { useSession } from '../../features/auth/session';

interface BalanceCardProps {
  caption?: string;
  showDeposit?: boolean;
}

/** Số điểm hiện có của người đang đăng nhập (GET /me/wallet). */
export function BalanceCard({ caption = 'Số điểm hiện có', showDeposit }: BalanceCardProps) {
  const [isDepositOpen, setIsDepositOpen] = useState(false);
  const wallet = useQuery({
    queryKey: queryKeys.myWallet,
    queryFn: api.myWallet,
    refetchOnWindowFocus: true,
  });
  const { state } = useSession();

  // Chỉ hiển thị nút Nạp điểm cho Sinh viên (vai trò SINH_VIEN).
  // Chủ tài khoản (CHU_TAI_KHOAN) thu tiền từ bãi xe, không nạp điểm vào ví.
  const isStudent = state.status === 'authenticated' && state.me.user.role === 'SINH_VIEN';
  const canDeposit = showDeposit !== undefined ? showDeposit : isStudent;

  return (
    <>
      <section className="relative rounded-xl bg-gradient-to-br from-blue-700 to-blue-900 p-6 text-white shadow-md">
        <div className="flex items-center justify-between">
          <p className="text-sm font-medium text-blue-100">{caption}</p>
          {canDeposit && (
            <button
              type="button"
              onClick={() => setIsDepositOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-lg bg-white/20 px-3 py-1 text-xs font-semibold text-white backdrop-blur-sm transition hover:bg-white/30"
            >
              <span>+ Nạp điểm</span>
              <span className="rounded bg-blue-500/60 px-1 py-0.5 text-[10px]">VietQR</span>
            </button>
          )}
        </div>
        {wallet.isPending && <Spinner className="mt-3 h-8 w-8" />}
        {wallet.isError && (
          <div className="mt-3">
            <QueryError error={wallet.error} onRetry={() => void wallet.refetch()} />
          </div>
        )}
        {wallet.data && (
          <p className="mt-2 text-4xl font-bold tracking-tight">
            {formatNumber(wallet.data.balance)} <span className="text-xl font-semibold text-blue-100">điểm</span>
          </p>
        )}
      </section>

      {isDepositOpen && (
        <DepositPointsDialog
          currentBalance={wallet.data?.balance}
          onClose={() => setIsDepositOpen(false)}
        />
      )}
    </>
  );
}
