import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { ConfirmDialog } from '../../shared/components/Dialog';
import { TextField } from '../../shared/components/Field';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { WithdrawalView } from '../../shared/types/api';
import { formatCash, formatDateTime, formatPoints } from '../../shared/utils/format';
import { parseAmount } from '../manager/amount';
import { WithdrawalStatusBadge } from './WithdrawalStatusBadge';

/**
 * Yêu cầu rút điểm của chủ bãi xe: tạo (xem trước số tiền), hủy khi còn chờ, sau khi quản lý trả tiền thì
 * "Đã nhận đủ" hoặc "Báo chưa nhận". Điểm chỉ bị trừ khi quản lý trả tiền.
 */
export function WithdrawalRequestsCard() {
  const queryClient = useQueryClient();
  const [points, setPoints] = useState('');
  const [complaining, setComplaining] = useState<WithdrawalView | null>(null);
  const [note, setNote] = useState('');
  const overview = useQuery({ queryKey: queryKeys.ownerOverview, queryFn: api.ownerOverview });
  const requests = useQuery({ queryKey: queryKeys.myWithdrawals, queryFn: api.myWithdrawalRequests, staleTime: 0 });
  const amount = parseAmount(points);
  const cashPerPoint = overview.data?.cashPerPoint ?? 1;

  async function refresh() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.myWithdrawals }),
      queryClient.invalidateQueries({ queryKey: queryKeys.ownerOverview }),
      queryClient.invalidateQueries({ queryKey: ['me'] }),
    ]);
  }

  const create = useMutation({
    mutationFn: (value: number) => api.createWithdrawalRequest(value),
    onSuccess: async () => {
      setPoints('');
      await refresh();
    },
  });
  const act = useMutation({
    mutationFn: ({ kind, id }: { kind: 'cancel' | 'confirm' | 'complain'; id: number }) =>
      kind === 'cancel' ? api.cancelWithdrawalRequest(id)
        : kind === 'confirm' ? api.confirmWithdrawalRequest(id)
          : api.complainWithdrawalRequest(id, note),
    onSuccess: async () => {
      setComplaining(null);
      setNote('');
      await refresh();
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (amount && amount > 0) create.mutate(amount);
  }

  return (
    <Card title="Yêu cầu rút điểm">
      <div className="space-y-4">
        <form onSubmit={submit} className="flex flex-wrap items-end gap-3" noValidate>
          <div className="min-w-48 flex-1">
            <TextField
              label="Số điểm muốn rút (nhận tiền mặt)"
              inputMode="numeric"
              value={points}
              onChange={(event) => setPoints(event.target.value)}
              hint={
                amount
                  ? `Sẽ nhận ${formatCash(amount * cashPerPoint)} tiền mặt từ quản lý`
                  : overview.data && `Còn rút được ${formatPoints(overview.data.available)}`
              }
            />
          </div>
          <Button type="submit" disabled={!amount || amount <= 0} loading={create.isPending}>
            Tạo yêu cầu
          </Button>
        </form>
        {create.isError && <Alert tone="error">{errorMessage(create.error)}</Alert>}
        {act.isError && !complaining && <Alert tone="error">{errorMessage(act.error)}</Alert>}

        {requests.isPending && <LoadingBlock />}
        {requests.isError && <QueryError error={requests.error} onRetry={() => void requests.refetch()} />}
        {requests.data?.length === 0 && <p className="py-4 text-center text-slate-500">Chưa có yêu cầu rút điểm nào.</p>}
        <ul className="divide-y divide-slate-100">
          {requests.data?.map((request) => (
            <li key={request.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="space-y-0.5">
                <p className="font-semibold">
                  #{request.id} · {formatPoints(request.points)} → {formatCash(request.cashAmount)}
                </p>
                <p className="text-xs text-slate-500">Tạo lúc {formatDateTime(request.createdAt)}</p>
                {request.processedByName && request.processedAt && (
                  <p className="text-xs text-slate-500">
                    {request.status === 'TU_CHOI' ? 'Từ chối' : 'Đã trả'} bởi {request.processedByName} lúc {formatDateTime(request.processedAt)}
                  </p>
                )}
                {request.rejectReason && <p className="text-sm text-slate-600">Lý do: {request.rejectReason}</p>}
                {request.complaintNote && <p className="text-sm text-red-700">Bạn báo: {request.complaintNote}</p>}
                {request.resolutionNote && <p className="text-sm text-emerald-700">Quản lý trả lời: {request.resolutionNote}</p>}
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <WithdrawalStatusBadge status={request.status} />
                {request.status === 'CHO_XU_LY' && (
                  <Button size="sm" variant="secondary" loading={act.isPending} onClick={() => act.mutate({ kind: 'cancel', id: request.id })}>
                    Hủy
                  </Button>
                )}
                {request.status === 'DA_TRA' && (
                  <>
                    <Button size="sm" variant="success" loading={act.isPending} onClick={() => act.mutate({ kind: 'confirm', id: request.id })}>
                      Đã nhận đủ
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => { act.reset(); setComplaining(request); }}>
                      Báo chưa nhận
                    </Button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>
      {complaining && (
        <ConfirmDialog
          title={`Báo chưa nhận đủ — yêu cầu #${complaining.id}`}
          confirmLabel="Gửi cho quản lý"
          tone="danger"
          pending={act.isPending}
          onCancel={() => setComplaining(null)}
          onConfirm={() => note.trim() && act.mutate({ kind: 'complain', id: complaining.id })}
          error={act.isError && <Alert tone="error">{errorMessage(act.error)}</Alert>}
        >
          <p className="text-sm text-slate-600">
            Điểm không được hoàn tự động (R10). Quản lý kiểm tra; nếu đúng là trả thiếu sẽ ghi giao dịch điều chỉnh.
          </p>
          <textarea
            rows={3}
            maxLength={500}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder={`Ví dụ: mới nhận ${formatCash(Math.max(0, complaining.cashAmount - 1000))}, thiếu 1.000 đ`}
            aria-label="Nội dung khiếu nại"
            className="block w-full rounded-lg border border-slate-300 px-3 py-2"
          />
        </ConfirmDialog>
      )}
    </Card>
  );
}
