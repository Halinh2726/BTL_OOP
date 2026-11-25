import { useQuery } from '@tanstack/react-query';
import { useParams } from 'react-router-dom';
import { guestApi } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Card, InfoRow } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { formatCash, formatDateTime, formatDuration } from '../../shared/utils/format';

export function GuestTicketPage() {
  const { token = '' } = useParams();
  const valid = /^[a-f0-9]{32}$/.test(token);
  const ticket = useQuery({
    queryKey: ['guest', 'ticket', token],
    queryFn: () => guestApi.publicTicket(token),
    enabled: valid,
    refetchInterval: (query) => query.state.data?.exitedAt ? false : 60_000,
  });
  const data = ticket.data;

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-lg flex-col justify-center p-4">
      <Card title="Vé gửi xe khách PTIT" actions={data && (
        <Badge tone={data.exitedAt ? 'gray' : 'green'}>{data.exitedAt ? 'Đã lấy xe' : 'Đang gửi'}</Badge>
      )}>
        {!valid && <Alert tone="error">Đường dẫn vé gửi xe không hợp lệ.</Alert>}
        {valid && ticket.isPending && <LoadingBlock label="Đang tải vé..." />}
        {ticket.isError && <QueryError error={ticket.error} onRetry={() => void ticket.refetch()} />}
        {data && (
          <div className="space-y-4">
            <div className="rounded-xl bg-blue-50 px-4 py-5">
              <p className="text-sm font-medium text-blue-800">{data.exitedAt ? 'Đã thanh toán' : 'Phí tạm tính'}</p>
              <p className="text-4xl font-bold text-blue-950">{formatCash(data.amount)}</p>
            </div>
            <dl>
              <InfoRow label="Giờ vào" value={formatDateTime(data.enteredAt)} />
              <InfoRow label="Thời gian gửi" value={formatDuration(data.minutesParked)} />
              {data.exitedAt && <InfoRow label="Giờ ra" value={formatDateTime(data.exitedAt)} />}
              {data.checkoutCurfewTime && <InfoRow label="Giờ khóa lấy xe" value={data.checkoutCurfewTime.slice(0, 5)} />}
            </dl>
            {data.checkoutLocked && <Alert tone="warning">Đã qua giờ khóa lấy xe. Bạn có thể lấy xe vào ngày hôm sau.</Alert>}
            {data.overnight && <p className="text-sm text-slate-600">Lượt này áp dụng phí qua đêm.</p>}
            {!data.exitedAt && <p className="text-sm text-slate-600">Giữ vé đã nhận tại chốt để xuất trình khi lấy xe.</p>}
          </div>
        )}
      </Card>
    </main>
  );
}
