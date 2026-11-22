import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { ComplaintType, StudentSessionView } from '../../shared/types/api';
import { formatDateTime, formatDuration, formatPoints } from '../../shared/utils/format';

interface MyParkingHistoryCardProps {
  onSelectSessionForComplaint?: (session: StudentSessionView, type: ComplaintType) => void;
}

/** Hiển thị xe đang gửi trong bãi và lịch sử lượt gửi gần đây, hỗ trợ mở nhanh yêu cầu trích xuất camera / khiếu nại. */
export function MyParkingHistoryCard({ onSelectSessionForComplaint }: MyParkingHistoryCardProps) {
  const sessions = useQuery({
    queryKey: queryKeys.mySessions,
    queryFn: api.mySessions,
    refetchInterval: 30_000,
  });

  return (
    <Card title="Xe đang gửi & Lịch sử ra vào bãi">
      {sessions.isPending && <LoadingBlock />}
      {sessions.isError && <QueryError error={sessions.error} onRetry={() => void sessions.refetch()} />}
      {sessions.data && (
        <div className="space-y-4">
          {sessions.data.parked.length > 0 && (
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">
                Đang gửi trong bãi ({sessions.data.parked.length})
              </p>
              <ul className="space-y-2">
                {sessions.data.parked.map((s) => (
                  <li
                    key={s.id}
                    className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-blue-200 bg-blue-50/60 px-4 py-3"
                  >
                    <div className="space-y-0.5 text-sm">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-base font-semibold text-slate-900">{s.plateDisplay}</span>
                        <Badge tone="blue">Đang trong bãi · {formatDuration(s.minutesParked)}</Badge>
                      </div>
                      <p className="text-xs text-slate-600">
                        Vào lúc {formatDateTime(s.enteredAt)}
                        {s.entryGuardName && <> · Bảo vệ trực: {s.entryGuardName}</>}
                      </p>
                    </div>
                    {onSelectSessionForComplaint && (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => onSelectSessionForComplaint(s, 'TRICH_XUAT_CAMERA')}
                      >
                        Trích xuất camera / Báo sự cố
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {sessions.data.recent.length === 0 ? (
            <p className="text-sm text-slate-500">Chưa có lượt gửi xe nào được ghi nhận.</p>
          ) : (
            <div className="space-y-2">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Lượt gửi gần đây ({sessions.data.recent.length})
              </p>
              <ul className="divide-y divide-slate-100 rounded-xl border border-slate-200">
                {sessions.data.recent.map((s) => (
                  <li key={s.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-2.5 text-sm">
                    <div className="space-y-0.5">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono font-semibold text-slate-900">{s.plateDisplay}</span>
                        {s.exitedAt === null ? (
                          <Badge tone="blue">Đang gửi</Badge>
                        ) : s.paidByPass ? (
                          <Badge tone="green">Miễn phí vé tháng</Badge>
                        ) : (
                          <Badge tone="gray">Phí {formatPoints(s.fee ?? 0)}</Badge>
                        )}
                      </div>
                      <p className="text-xs text-slate-500">
                        Vào {formatDateTime(s.enteredAt)}
                        {s.exitedAt ? ` → Ra ${formatDateTime(s.exitedAt)}` : ''} · {formatDuration(s.minutesParked)}
                        {s.paymentTransactionId !== null && <> · GD #{s.paymentTransactionId}</>}
                      </p>
                    </div>
                    {onSelectSessionForComplaint && (
                      <div className="flex flex-wrap gap-1.5">
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => onSelectSessionForComplaint(s, 'TRICH_XUAT_CAMERA')}
                        >
                          Xem camera
                        </Button>
                        {s.fee !== null && s.fee > 0 && (
                          <Button
                            size="sm"
                            variant="secondary"
                            onClick={() => onSelectSessionForComplaint(s, 'THU_PHI_SAI')}
                          >
                            Khiếu nại phí
                          </Button>
                        )}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}
