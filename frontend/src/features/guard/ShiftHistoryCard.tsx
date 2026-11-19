import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Badge } from '../../shared/components/Badge';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { formatCash, formatDateTime, formatDuration, formatTime } from '../../shared/utils/format';
import { CashDifference } from './CashDifference';

/** Lịch sử trực ca của tôi — chấm công cá nhân: ngày, giờ nhận / kết thúc ca, số giờ trực, lượt vào/ra, tiền bàn giao. */
export function ShiftHistoryCard() {
  const history = useQuery({ queryKey: queryKeys.myShiftHistory, queryFn: api.myShiftHistory });
  // Tháng hiện tại theo giờ Việt Nam (startedAt đã là giờ Việt Nam)
  const thisMonth = new Date(Date.now() + 7 * 3600_000).toISOString().slice(0, 7);
  const monthMinutes = (history.data ?? [])
    .filter((shift) => shift.startedAt.startsWith(thisMonth))
    .reduce((sum, shift) => sum + shift.workedMinutes, 0);

  return (
    <Card
      title={
        <>
          Lịch sử trực ca (chấm công){' '}
          {history.data && (
            <span className="text-sm font-normal text-slate-500">· tháng này {formatDuration(monthMinutes)}</span>
          )}
        </>
      }
    >
      {history.isPending && <LoadingBlock />}
      {history.isError && <QueryError error={history.error} onRetry={() => void history.refetch()} />}
      {history.data?.length === 0 && <p className="py-6 text-center text-slate-500">Bạn chưa có ca trực nào.</p>}
      {history.data && history.data.length > 0 && (
        <div className="-mx-5 overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-5 py-2 font-medium">Ca</th>
                <th className="px-3 py-2 font-medium">Thời gian trực</th>
                <th className="px-3 py-2 text-right font-medium">Vào / Ra</th>
                <th className="px-3 py-2 text-right font-medium">Tiền nhận ca</th>
                <th className="px-5 py-2 text-right font-medium">Tiền bàn giao</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {history.data.map((shift) => (
                <tr key={shift.id}>
                  <td className="px-5 py-2">
                    <p className="font-medium">{formatDateTime(shift.startedAt).slice(0, 10)}</p>
                    <p className="text-slate-500">
                      {formatTime(shift.startedAt)} – {shift.endedAt ? formatTime(shift.endedAt) : <Badge tone="green">đang trực</Badge>}
                    </p>
                  </td>
                  <td className="px-3 py-2">{formatDuration(shift.workedMinutes)}</td>
                  <td className="px-3 py-2 text-right">
                    {shift.summary ? `${shift.summary.checkIns} / ${shift.summary.checkOuts}` : '—'}
                  </td>
                  <td className="px-3 py-2 text-right">
                    {formatCash(shift.openingCash)}
                    <CashDifference difference={shift.openingDiscrepancy} />
                  </td>
                  <td className="px-5 py-2 text-right">
                    {shift.closingCash === null ? '—' : formatCash(shift.closingCash)}
                    {shift.receivedByName && <p className="text-xs text-slate-500">→ {shift.receivedByName}</p>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
