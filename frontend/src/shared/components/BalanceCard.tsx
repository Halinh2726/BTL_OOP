import { useQuery } from '@tanstack/react-query';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { formatNumber } from '../utils/format';
import { QueryError } from './QueryError';
import { Spinner } from './Spinner';

/** Số điểm hiện có của người đang đăng nhập (GET /me/wallet). */
export function BalanceCard({ caption = 'Số điểm hiện có' }: { caption?: string }) {
  const wallet = useQuery({ queryKey: queryKeys.myWallet, queryFn: api.myWallet });

  return (
    <section className="rounded-xl bg-gradient-to-br from-blue-700 to-blue-900 p-6 text-white shadow-md">
      <p className="text-sm font-medium text-blue-100">{caption}</p>
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
  );
}
