import { useInfiniteQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import type { TransactionType } from '../types/api';
import { formatDateTime, formatNumber, formatSignedPoints, TRANSACTION_LABELS } from '../utils/format';
import { Button } from './Button';
import { Card } from './Card';
import { QueryError } from './QueryError';
import { LoadingBlock } from './Spinner';

const PAGE_SIZE = 20;
const TYPES = Object.keys(TRANSACTION_LABELS) as TransactionType[];

interface TransactionHistoryProps {
  /** Loại lọc mặc định; undefined = tất cả. */
  defaultType?: TransactionType;
  /** Chỉ hiện các loại liên quan tới vai trò. */
  types?: TransactionType[];
}

/** Lịch sử giao dịch của ví đang đăng nhập (GET /me/transactions), phân trang bằng "Xem thêm". */
export function TransactionHistory({ defaultType, types = TYPES }: TransactionHistoryProps) {
  const [type, setType] = useState<TransactionType | undefined>(defaultType);
  const history = useInfiniteQuery({
    queryKey: queryKeys.myTransactions(type),
    queryFn: ({ pageParam }) => api.myTransactions({ type, limit: PAGE_SIZE, before: pageParam }),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last.hasMore && last.nextBefore !== null ? last.nextBefore : undefined),
  });
  const items = history.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <Card
      title="Lịch sử giao dịch"
      actions={
        <select
          aria-label="Lọc theo loại giao dịch"
          value={type ?? ''}
          onChange={(event) => setType((event.target.value || undefined) as TransactionType | undefined)}
          className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm"
        >
          <option value="">Tất cả</option>
          {types.map((code) => (
            <option key={code} value={code}>
              {TRANSACTION_LABELS[code]}
            </option>
          ))}
        </select>
      }
    >
      {history.isPending && <LoadingBlock />}
      {history.isError && <QueryError error={history.error} onRetry={() => void history.refetch()} />}
      {history.isSuccess && items.length === 0 && <p className="py-6 text-center text-slate-500">Chưa có giao dịch nào.</p>}
      {items.length > 0 && (
        <ul className="-mx-5 divide-y divide-slate-100 sm:hidden">
          {items.map((item) => (
            <li key={`${item.id}-${item.type}`} className="px-5 py-3">
              <div className="flex items-baseline justify-between gap-3">
                <span className="font-medium">{TRANSACTION_LABELS[item.type]}</span>
                <span className={`font-semibold ${item.change >= 0 ? 'text-emerald-700' : 'text-red-600'}`}>
                  {formatSignedPoints(item.change)}
                </span>
              </div>
              <div className="mt-0.5 flex justify-between gap-3 text-xs text-slate-500">
                <span>{formatDateTime(item.createdAt)}</span>
                <span>Số dư sau: {formatNumber(item.balanceAfter)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
      {items.length > 0 && (
        <div className="-mx-5 hidden overflow-x-auto sm:block">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-5 py-2 font-medium">Thời gian</th>
                <th className="px-3 py-2 font-medium">Loại</th>
                <th className="px-3 py-2 text-right font-medium">Thay đổi</th>
                <th className="px-5 py-2 text-right font-medium">Số dư sau</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {items.map((item) => (
                <tr key={`${item.id}-${item.type}`}>
                  <td className="whitespace-nowrap px-5 py-2.5 text-slate-600">{formatDateTime(item.createdAt)}</td>
                  <td className="px-3 py-2.5">{TRANSACTION_LABELS[item.type]}</td>
                  <td
                    className={`whitespace-nowrap px-3 py-2.5 text-right font-semibold ${
                      item.change >= 0 ? 'text-emerald-700' : 'text-red-600'
                    }`}
                  >
                    {formatSignedPoints(item.change)}
                  </td>
                  <td className="whitespace-nowrap px-5 py-2.5 text-right">{formatNumber(item.balanceAfter)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {history.hasNextPage && (
        <div className="mt-4 text-center">
          <Button variant="secondary" onClick={() => void history.fetchNextPage()} loading={history.isFetchingNextPage}>
            Xem thêm
          </Button>
        </div>
      )}
    </Card>
  );
}
