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
import type { ComplaintView } from '../../shared/types/api';
import { formatPoints } from '../../shared/utils/format';
import { ComplaintBody, ComplaintStatusBadge } from '../complaints/ComplaintParts';

type Action = { kind: 'approve' | 'reject'; complaint: ComplaintView };

/**
 * Đợt 4b — chủ bãi xe duyệt / từ chối đề xuất bồi thường của quản lý. Duyệt thì điểm chuyển từ ví chủ bãi sang ví
 * sinh viên ngay (giao dịch điều chỉnh); bấm duyệt lần hai không trả lần hai.
 */
export function CompensationsCard() {
  const queryClient = useQueryClient();
  const [action, setAction] = useState<Action | null>(null);
  const [note, setNote] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const list = useQuery({ queryKey: queryKeys.ownerCompensations, queryFn: api.ownerCompensations, staleTime: 0 });
  const pending = list.data?.filter((c) => c.status === 'CHO_DUYET_BOI_THUONG').length ?? 0;

  const decide = useMutation({
    mutationFn: async ({ kind, complaint }: Action) =>
      kind === 'approve' ? (await api.approveCompensation(complaint.id, note)).complaint : api.rejectCompensation(complaint.id, note),
    onSuccess: async (c) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.ownerCompensations }),
        queryClient.invalidateQueries({ queryKey: queryKeys.ownerOverview }),
        queryClient.invalidateQueries({ queryKey: queryKeys.myTransactionsAll }),
      ]);
      setAction(null);
      setMessage(c.status === 'DA_BOI_THUONG'
        ? `Đã chuyển ${formatPoints(c.proposedPoints ?? 0)} bồi thường cho ${c.studentName ?? 'sinh viên'}.`
        : `Đã từ chối đề xuất bồi thường #${c.id}.`);
    },
  });

  return (
    <Card title={`Đề xuất bồi thường${pending ? ` (${pending} chờ duyệt)` : ''}`}>
      <div className="space-y-3">
        {message && <Alert tone="success">{message}</Alert>}
        {list.isPending && <LoadingBlock />}
        {list.isError && <QueryError error={list.error} onRetry={() => void list.refetch()} />}
        {list.data?.length === 0 && <p className="text-sm text-slate-500">Chưa có đề xuất bồi thường nào.</p>}
        <ul className="divide-y divide-slate-100">
          {list.data?.map((c) => (
            <li key={c.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
              <div className="min-w-0 flex-1"><ComplaintBody complaint={c} viewer="owner" /></div>
              <div className="flex flex-wrap items-center justify-end gap-2">
                <ComplaintStatusBadge status={c.status} />
                {c.status === 'CHO_DUYET_BOI_THUONG' && (
                  <>
                    <Button size="sm" variant="success" onClick={() => { decide.reset(); setNote(''); setAction({ kind: 'approve', complaint: c }); }}>
                      Duyệt
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => { decide.reset(); setNote(''); setAction({ kind: 'reject', complaint: c }); }}>
                      Từ chối
                    </Button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>
      {action && (
        <ConfirmDialog
          title={action.kind === 'approve' ? `Duyệt bồi thường ${formatPoints(action.complaint.proposedPoints ?? 0)}` : 'Từ chối bồi thường'}
          confirmLabel={action.kind === 'approve' ? 'Duyệt — chuyển điểm' : 'Từ chối'}
          tone={action.kind === 'approve' ? 'success' : 'danger'}
          pending={decide.isPending}
          onCancel={() => setAction(null)}
          onConfirm={() => (action.kind === 'approve' || note.trim() ? decide.mutate(action) : undefined)}
          error={decide.isError && <Alert tone="error">{errorMessage(decide.error)}</Alert>}
        >
          <div className="space-y-2">
            {action.kind === 'approve' && (
              <p className="text-sm">
                Chuyển <strong>{formatPoints(action.complaint.proposedPoints ?? 0)}</strong> từ ví của bạn sang ví của{' '}
                {action.complaint.studentName}. Giao dịch được ghi vào sổ và không hoàn tác được.
              </p>
            )}
            <label className="block space-y-1 text-sm font-medium text-slate-700">
              {action.kind === 'approve' ? 'Ghi chú (không bắt buộc)' : 'Lý do từ chối (bắt buộc)'}
              <textarea rows={2} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} className="block w-full rounded-lg border border-slate-300 px-3 py-2" />
            </label>
          </div>
        </ConfirmDialog>
      )}
    </Card>
  );
}
