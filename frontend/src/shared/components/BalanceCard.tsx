import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useSession } from '../../features/auth/session';
import { DepositPointsDialog } from '../../features/student/DepositPointsDialog';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { formatNumber } from '../utils/format';
import { QueryError } from './QueryError';
import { Spinner } from './Spinner';

/** Số điểm hiện có của người đang đăng nhập (GET /me/wallet). Sinh viên có nút Nạp điểm VietQR nhanh. */
export function BalanceCard({ caption = 'Số điểm hiện có' }: { caption?: string }) {
  const session = useSession();
  const isStudent = session.state.status === 'authenticated' && session.state.me.user.role === 'SINH_VIEN';
  const [openDeposit, setOpenDeposit] = useState(false);

  const wallet = useQuery({
    queryKey: queryKeys.myWallet,
    queryFn: api.myWallet,
    refetchOnWindowFocus: true,
  });

  return (
    <>
      <section className="rounded-xl bg-gradient-to-br from-blue-700 to-blue-900 p-6 text-white shadow-md">
        <div className="flex items-center justify-between gap-3">
          <p className="text-sm font-medium text-blue-100">{caption}</p>
          {isStudent && (
            <button
              type="button"
              onClick={() => setOpenDeposit(true)}
              className="inline-flex items-center gap-1.5 rounded-lg bg-white/15 px-3 py-1.5 text-xs font-semibold text-white ring-1 ring-white/25 transition hover:bg-white/25"
            >
              <span>+ Nạp điểm</span>
              <span className="rounded bg-blue-500/80 px-1.5 py-0.5 text-[10px] font-bold uppercase tracking-wide">
                VietQR
              </span>
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
      {openDeposit && isStudent && (
        <DepositPointsDialog onClose={() => setOpenDeposit(false)} currentBalance={wallet.data?.balance} />
      )}
    </>
  );
}
