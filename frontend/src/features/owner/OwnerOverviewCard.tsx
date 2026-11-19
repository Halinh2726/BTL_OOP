import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { formatCash, formatNumber, formatPoints } from '../../shared/utils/format';

/** Tổng quan tài chính của bãi — chủ bãi tự xem, không phải tin con số quản lý báo cáo. */
export function OwnerOverviewCard() {
  const overview = useQuery({ queryKey: queryKeys.ownerOverview, queryFn: api.ownerOverview, staleTime: 0 });

  if (overview.isPending) return <LoadingBlock />;
  if (overview.isError) return <QueryError error={overview.error} onRetry={() => void overview.refetch()} />;
  const data = overview.data;

  const tiles = [
    { label: 'Số điểm hiện có', value: formatPoints(data.balance), hint: `≈ ${formatCash(data.balance * data.cashPerPoint)}`, strong: true },
    { label: 'Đang chờ rút', value: formatPoints(data.pendingPoints), hint: `Còn rút được ${formatPoints(data.available)}` },
    { label: 'Doanh thu hôm nay', value: formatPoints(data.revenueToday), hint: `${formatNumber(data.paymentsToday)} lượt xe ra` },
    { label: 'Doanh thu tháng này', value: formatPoints(data.revenueMonth), hint: 'Phí gửi xe đã thu' },
    { label: 'Quỹ tiền mặt (quản lý giữ)', value: formatCash(data.cashHeld), hint: 'Tiền đổi điểm − tiền đã trả khi rút' },
    { label: 'Quỹ điện tử', value: 'Chưa áp dụng', hint: 'Cần chức năng nạp điểm online' },
  ];

  return (
    <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" aria-label="Tổng quan tài chính">
      {tiles.map((tile) => (
        <div
          key={tile.label}
          className={`rounded-xl p-4 shadow-sm ring-1 ${tile.strong ? 'bg-blue-700 text-white ring-blue-700' : 'bg-white ring-slate-200'}`}
        >
          <p className={`text-sm ${tile.strong ? 'text-blue-100' : 'text-slate-500'}`}>{tile.label}</p>
          <p className="mt-1 text-2xl font-bold">{tile.value}</p>
          <p className={`text-xs ${tile.strong ? 'text-blue-100' : 'text-slate-400'}`}>{tile.hint}</p>
        </div>
      ))}
    </section>
  );
}
