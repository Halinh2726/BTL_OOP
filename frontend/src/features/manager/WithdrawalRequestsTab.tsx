import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { ConfirmDialog } from '../../shared/components/Dialog';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { WithdrawalStatus, WithdrawalView } from '../../shared/types/api';
import { WITHDRAWAL_STATUS_LABELS, formatCash, formatDateTime, formatPoints } from '../../shared/utils/format';
import { WithdrawalStatusBadge } from '../owner/WithdrawalStatusBadge';

type Action = { kind: 'pay' | 'reject' | 'resolve'; request: WithdrawalView };

const FILTERS: Array<WithdrawalStatus | undefined> = ['CHO_XU_LY', 'KHIEU_NAI', 'DA_TRA', undefined];

/**
 * Quản lý xử lý yêu cầu rút điểm của chủ bãi xe (thay cho "rút điểm tự do" đã bỏ): trả tiền mặt — lúc này điểm mới bị
 * trừ —, từ chối kèm lý do, kiểm tra khiếu nại "chưa nhận đủ". Quỹ tiền mặt hiện có luôn hiện trên đầu (R16).
 */
export function WithdrawalRequestsTab() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<WithdrawalStatus | undefined>('CHO_XU_LY');
  const [action, setAction] = useState<Action | null>(null);
  const [note, setNote] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const queue = useQuery({ queryKey: queryKeys.withdrawalQueue(status), queryFn: () => api.withdrawalQueue(status), staleTime: 0 });

  const act = useMutation({
    mutationFn: async ({ kind, request }: Action) => {
      if (kind === 'pay') {
        const result = await api.payWithdrawalRequest(request.id);
        return result.replayed ? `Yêu cầu #${request.id} đã được trả từ trước — không trừ điểm lần hai.` : `Đã trả ${formatCash(request.cashAmount)} cho yêu cầu #${request.id}.`;
      }
      if (kind === 'reject') {
        await api.rejectWithdrawalRequest(request.id, note);
        return `Đã từ chối yêu cầu #${request.id}.`;
      }
      await api.resolveWithdrawalRequest(request.id, note);
      return `Đã đóng khiếu nại của yêu cầu #${request.id}.`;
    },
    onSuccess: async (text) => {
      // Tải lại danh sách TRƯỚC rồi mới báo thành công: không còn khoảnh khắc thấy nút của trạng thái cũ
      await queryClient.invalidateQueries({ queryKey: queryKeys.withdrawalQueueAll });
      await queryClient.invalidateQueries({ queryKey: queryKeys.ownerWallet });
      setAction(null);
      setNote('');
      setMessage(text);
    },
  });

  const cashHeld = queue.data?.cashHeld ?? 0;

  return (
    <Card title="Yêu cầu rút điểm của chủ bãi xe">
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="rounded-xl bg-emerald-50 p-4">
            <p className="text-sm text-emerald-800">Quỹ tiền mặt đang giữ (R13)</p>
            <p className="text-3xl font-bold text-emerald-900">{queue.data ? formatCash(cashHeld) : '—'}</p>
          </div>
          <div className="rounded-xl bg-slate-50 p-4 text-sm text-slate-600">
            Chỉ chủ bãi xe <strong>{queue.data?.ownerName}</strong> tạo được yêu cầu. Điểm bị trừ khi bạn bấm "Đã trả tiền";
            không trả được nếu quỹ tiền mặt không đủ (R16).
          </div>
        </div>
        <div className="flex flex-wrap gap-2">
          {FILTERS.map((filter) => (
            <button
              key={filter ?? 'ALL'}
              type="button"
              onClick={() => setStatus(filter)}
              className={`rounded-full px-3 py-1 text-sm font-semibold ring-1 ring-inset ${
                status === filter ? 'bg-blue-700 text-white ring-blue-700' : 'bg-white text-slate-600 ring-slate-300'
              }`}
            >
              {filter ? WITHDRAWAL_STATUS_LABELS[filter] : 'Tất cả'}
            </button>
          ))}
        </div>
        {message && <Alert tone="success">{message}</Alert>}
        {queue.isPending && <LoadingBlock />}
        {queue.isError && <QueryError error={queue.error} onRetry={() => void queue.refetch()} />}
        {queue.data?.items.length === 0 && <p className="py-6 text-center text-slate-500">Không có yêu cầu nào.</p>}
        <ul className="divide-y divide-slate-100">
          {queue.data?.items.map((request) => (
            <li key={request.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
              <div className="space-y-1">
                <p className="font-semibold">
                  #{request.id} · {formatPoints(request.points)} → <span className="text-emerald-700">{formatCash(request.cashAmount)}</span>
                </p>
                <p className="text-xs text-slate-500">Tạo lúc {formatDateTime(request.createdAt)}</p>
                {request.complaintNote && <p className="text-sm text-red-700">Khiếu nại: {request.complaintNote}</p>}
                {request.rejectReason && <p className="text-sm text-slate-600">Lý do từ chối: {request.rejectReason}</p>}
                {request.processedByName && request.processedAt && (
                  <p className="text-xs text-slate-500">
                    {request.status === 'TU_CHOI' ? 'Từ chối' : 'Trả tiền'} bởi {request.processedByName} lúc {formatDateTime(request.processedAt)}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-2">
                <WithdrawalStatusBadge status={request.status} />
                {request.status === 'CHO_XU_LY' && (
                  <>
                    <Button
                      size="sm"
                      variant="success"
                      disabled={request.cashAmount > cashHeld}
                      title={request.cashAmount > cashHeld ? 'Quỹ tiền mặt không đủ' : undefined}
                      onClick={() => {
                        act.reset();
                        setAction({ kind: 'pay', request });
                      }}
                    >
                      Đã trả tiền
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => { act.reset(); setAction({ kind: 'reject', request }); }}>
                      Từ chối
                    </Button>
                  </>
                )}
                {request.status === 'KHIEU_NAI' && (
                  <Button size="sm" onClick={() => { act.reset(); setAction({ kind: 'resolve', request }); }}>
                    Đã kiểm tra
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>

      {action && (
        <ConfirmDialog
          title={action.kind === 'pay' ? 'Xác nhận đã trả tiền mặt' : action.kind === 'reject' ? 'Từ chối yêu cầu rút' : 'Đóng khiếu nại'}
          confirmLabel={action.kind === 'pay' ? 'Đã trả tiền — trừ điểm' : action.kind === 'reject' ? 'Từ chối' : 'Đã kiểm tra xong'}
          tone={action.kind === 'reject' ? 'danger' : 'success'}
          pending={act.isPending}
          onCancel={() => setAction(null)}
          onConfirm={() => (action.kind === 'pay' || note.trim() ? act.mutate(action) : undefined)}
          error={act.isError && <Alert tone="error">{errorMessage(act.error)}</Alert>}
        >
          {action.kind === 'pay' ? (
            <p>
              Trả <strong>{formatCash(action.request.cashAmount)}</strong> tiền mặt cho chủ bãi xe? Hệ thống trừ{' '}
              {formatPoints(action.request.points)} khỏi ví chủ bãi và ghi giao dịch rút điểm.
            </p>
          ) : (
            <div className="space-y-1">
              <label htmlFor="withdrawal-note" className="block text-sm font-medium text-slate-700">
                {action.kind === 'reject' ? 'Lý do từ chối (bắt buộc)' : 'Kết quả kiểm tra (bắt buộc)'}
              </label>
              <textarea
                id="withdrawal-note"
                rows={3}
                maxLength={500}
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder={action.kind === 'reject' ? 'Ví dụ: quỹ đang kiểm kê, tạo lại sau 17:00' : 'Ví dụ: đã đếm lại, trả bù 1.000 đ — đã ghi điều chỉnh'}
                className="block w-full rounded-lg border border-slate-300 px-3 py-2"
              />
            </div>
          )}
        </ConfirmDialog>
      )}
    </Card>
  );
}
