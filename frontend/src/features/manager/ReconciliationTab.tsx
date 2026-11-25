import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { ConfirmDialog } from '../../shared/components/Dialog';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { ClosedReconciliation, ReconciliationOutcome } from '../../shared/types/api';
import { formatCash, formatDateTime, formatNumber, formatPoints, formatTime, RECONCILIATION_OUTCOME_LABELS } from '../../shared/utils/format';
import { vietnamToday } from '../owner/revenue/revenueMath';
import { parseAmount } from './amount';

const OUTCOME_TONE: Record<ReconciliationOutcome, 'green' | 'amber' | 'red'> = { KHOP: 'green', THUA: 'amber', THIEU: 'red' };

function signedCash(value: number): string {
  return value === 0 ? '0 đ' : `${value > 0 ? '+' : '−'}${formatCash(Math.abs(value))}`;
}

/**
 * Đợt 4a — đối soát tiền mặt cuối ngày: sổ sách (đầu ngày + vào − ra = phải có, R13), kiểm tra bảo toàn điểm (R12),
 * đếm tiền thực tế rồi CHỐT. Biên bản đã chốt không sửa được; lệch thì bắt buộc giải trình.
 */
export function ReconciliationTab() {
  const today = vietnamToday();
  const queryClient = useQueryClient();
  const [date, setDate] = useState(today);
  const [counted, setCounted] = useState('');
  const [note, setNote] = useState('');
  const [confirming, setConfirming] = useState(false);
  const day = useQuery({ queryKey: queryKeys.reconciliationDay(date), queryFn: () => api.reconciliationDay(date), enabled: date <= today });
  const gateCash = useQuery({ queryKey: queryKeys.managerGateCash(date), queryFn: () => api.managerGateCash(date), enabled: date <= today });
  const history = useQuery({ queryKey: queryKeys.reconciliationHistory, queryFn: api.reconciliationHistory });

  const countedCash = counted.trim() === '' ? null : parseAmount(counted);
  const difference = countedCash !== null && day.data ? countedCash - day.data.expectedCash : null;
  const needsNote = difference !== null && difference !== 0;
  const canClose = countedCash !== null && (!needsNote || note.trim().length > 0);

  const close = useMutation({
    mutationFn: () => api.closeReconciliation({ date, countedCash: countedCash ?? 0, note: note.trim() || undefined }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.reconciliationAll });
      setConfirming(false);
      setCounted('');
      setNote('');
    },
  });

  return (
    <div className="space-y-6">
      <Card
        title="Đối soát tiền mặt cuối ngày"
        actions={
          <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
            Ngày
            <input
              type="date"
              value={date}
              max={today}
              onChange={(event) => { setDate(event.target.value); close.reset(); }}
              className="rounded-lg border border-slate-300 px-3 py-1.5"
            />
          </label>
        }
      >
        {day.isPending && <LoadingBlock />}
        {day.isError && <QueryError error={day.error} onRetry={() => void day.refetch()} />}
        {day.data && (
          <div className="space-y-5">
            <div className="grid gap-3 sm:grid-cols-4" style={{ fontVariantNumeric: 'tabular-nums' }}>
              <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs text-slate-500">Tiền đầu ngày</p><p className="text-lg font-bold">{formatCash(day.data.openingCash)}</p></div>
              <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs text-slate-500">+ Tiền vào ({day.data.deposits} lần đổi điểm)</p><p className="text-lg font-bold">{formatCash(day.data.cashIn)}</p></div>
              <div className="rounded-xl bg-slate-50 p-3"><p className="text-xs text-slate-500">− Tiền ra ({day.data.withdrawals} lần rút điểm)</p><p className="text-lg font-bold">{formatCash(day.data.cashOut)}</p></div>
              <div className="rounded-xl bg-blue-50 p-3 ring-1 ring-blue-200"><p className="text-xs text-blue-800">= Phải có trong két (R13)</p><p className="text-lg font-bold text-blue-900">{formatCash(day.data.expectedCash)}</p></div>
            </div>
            <div className="flex flex-wrap items-center gap-3 text-sm text-slate-600">
              <span>Trong ngày: {formatNumber(day.data.payments)} lượt xe ra, doanh thu {formatPoints(day.data.revenue)}.</span>
              {day.data.pointsBalanced
                ? <Badge tone="green">✓ Bảo toàn điểm (R12)</Badge>
                : <Badge tone="red">✗ Tổng điểm KHÔNG khớp (R12) — báo kỹ thuật</Badge>}
            </div>

            {day.data.closed ? (
              <ClosedCard closed={day.data.closed} />
            ) : (
              <div className="space-y-3 rounded-xl p-4 ring-1 ring-slate-200">
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="block space-y-1 text-sm font-medium text-slate-700">
                    Tiền đếm được thực tế
                    <span className="relative block">
                      <input
                        inputMode="numeric"
                        value={counted}
                        onChange={(event) => setCounted(event.target.value)}
                        placeholder="0"
                        aria-invalid={counted.trim() !== '' && countedCash === null ? true : undefined}
                        className="block w-full rounded-lg border border-slate-300 px-3 py-2 pr-10 text-right font-mono text-xl"
                      />
                      <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-slate-500">đ</span>
                    </span>
                    {counted.trim() !== '' && countedCash === null && <span className="block text-sm text-red-600">Chỉ gồm chữ số</span>}
                  </label>
                  <div className="flex flex-col justify-end">
                    {difference !== null && (
                      <p className={`rounded-lg px-3 py-2 text-sm font-semibold ${difference === 0 ? 'bg-emerald-50 text-emerald-800' : 'bg-red-50 text-red-800'}`}>
                        {difference === 0 ? 'Khớp sổ sách' : `${difference > 0 ? 'Thừa' : 'Thiếu'} ${formatCash(Math.abs(difference))} so với sổ sách`}
                      </p>
                    )}
                  </div>
                </div>
                <label className="block space-y-1 text-sm font-medium text-slate-700">
                  Giải trình {needsNote ? '(bắt buộc vì lệch)' : '(không bắt buộc)'}
                  <textarea
                    rows={2}
                    maxLength={500}
                    value={note}
                    onChange={(event) => setNote(event.target.value)}
                    placeholder="Ví dụ: trả nhầm tiền thừa 1.000 đ cho sinh viên lúc 15:20"
                    className="block w-full rounded-lg border border-slate-300 px-3 py-2"
                  />
                </label>
                {close.isError && !confirming && <Alert tone="error">{errorMessage(close.error)}</Alert>}
                <Button disabled={!canClose} onClick={() => { close.reset(); setConfirming(true); }}>
                  Chốt sổ ngày {date.split('-').reverse().join('/')}
                </Button>
              </div>
            )}
          </div>
        )}
      </Card>

      <Card title={`Tiền mặt bảo vệ thu tại chốt ngày ${date.split('-').reverse().join('/')}`}>
        {gateCash.isPending && <LoadingBlock />}
        {gateCash.isError && <QueryError error={gateCash.error} onRetry={() => void gateCash.refetch()} />}
        {gateCash.data && (
          <div className="space-y-4">
            <p className="text-xs text-slate-600">
              Khoản này bảo vệ thu trực tiếp bằng tiền mặt cho các lượt xe ra tại cổng, theo dõi tách biệt với sổ đổi điểm (
              <code className="font-mono">DOI_DIEM</code>) để đảm bảo bảo toàn điểm (R12).
            </p>
            <div className="flex flex-wrap items-center gap-3" style={{ fontVariantNumeric: 'tabular-nums' }}>
              <div className="rounded-xl bg-amber-50 px-4 py-3 ring-1 ring-amber-200">
                <p className="text-xs text-amber-800">Tổng thu tiền mặt tại cổng ({gateCash.data.totalCount} lượt)</p>
                <p className="text-xl font-bold text-amber-900">{formatCash(gateCash.data.totalAmount)}</p>
              </div>
              {gateCash.data.byGuard.map((g) => (
                <div key={g.guardId} className="rounded-xl bg-slate-50 px-4 py-3 ring-1 ring-slate-200">
                  <p className="text-xs text-slate-500">
                    {g.guardName} ({g.count} lượt)
                  </p>
                  <p className="text-base font-bold text-slate-900">{formatCash(g.totalAmount)}</p>
                </div>
              ))}
            </div>
            {gateCash.data.entries.length === 0 ? (
              <p className="text-sm text-slate-500">Không có lượt thu tiền mặt tại chốt trong ngày này.</p>
            ) : (
              <div className="-mx-5 overflow-x-auto">
                <table className="min-w-full text-sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
                  <thead className="bg-slate-50 text-left text-slate-500">
                    <tr>
                      <th className="px-5 py-2 font-medium">Giờ thu</th>
                      <th className="px-3 py-2 font-medium">Biển số</th>
                      <th className="px-3 py-2 font-medium">Sinh viên</th>
                      <th className="px-3 py-2 font-medium">Bảo vệ thu</th>
                      <th className="px-3 py-2 font-medium">Loại phí</th>
                      <th className="px-5 py-2 text-right font-medium">Số tiền</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {gateCash.data.entries.map((entry) => (
                      <tr key={`${Boolean(entry.guest)}-${entry.collectionId}`}>
                        <td className="whitespace-nowrap px-5 py-2 text-slate-600">{formatTime(entry.collectedAt)}</td>
                        <td className="whitespace-nowrap px-3 py-2 font-mono font-semibold">{entry.plateDisplay}</td>
                        <td className="px-3 py-2">
                          {entry.fullName} <span className="text-xs text-slate-500">({entry.studentCode})</span>
                        </td>
                        <td className="px-3 py-2">{entry.guardName}</td>
                        <td className="px-3 py-2">
                          {entry.overnight ? <Badge tone="amber">Qua đêm</Badge> : <Badge tone="gray">Thường</Badge>}
                        </td>
                        <td className="whitespace-nowrap px-5 py-2 text-right font-semibold">{formatCash(entry.amount)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </Card>

      <Card title="Các biên bản đã chốt">
        {history.isPending && <LoadingBlock />}
        {history.isError && <QueryError error={history.error} onRetry={() => void history.refetch()} />}
        {history.data?.length === 0 && <p className="text-sm text-slate-500">Chưa chốt ngày nào.</p>}
        {history.data && history.data.length > 0 && (
          <div className="-mx-5 overflow-x-auto">
            <table className="min-w-full text-sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
              <thead className="bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-5 py-2 font-medium">Ngày</th>
                  <th className="px-3 py-2 text-right font-medium">Phải có</th>
                  <th className="px-3 py-2 text-right font-medium">Đếm được</th>
                  <th className="px-3 py-2 text-right font-medium">Chênh lệch</th>
                  <th className="px-3 py-2 font-medium">Kết luận</th>
                  <th className="px-5 py-2 font-medium">Người chốt</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {history.data.map((r) => (
                  <tr key={r.date} className="cursor-pointer hover:bg-slate-50" onClick={() => setDate(r.date)}>
                    <td className="px-5 py-2 font-medium">{r.date.split('-').reverse().join('/')}</td>
                    <td className="px-3 py-2 text-right">{formatCash(r.expectedCash)}</td>
                    <td className="px-3 py-2 text-right">{formatCash(r.countedCash)}</td>
                    <td className="px-3 py-2 text-right">{signedCash(r.discrepancy)}</td>
                    <td className="px-3 py-2"><Badge tone={OUTCOME_TONE[r.outcome]}>{RECONCILIATION_OUTCOME_LABELS[r.outcome]}</Badge></td>
                    <td className="px-5 py-2">{r.closedByName}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {confirming && day.data && countedCash !== null && (
        <ConfirmDialog
          title="Chốt sổ — không sửa được sau khi chốt"
          confirmLabel="Chốt sổ"
          tone={needsNote ? 'danger' : 'success'}
          pending={close.isPending}
          onCancel={() => setConfirming(false)}
          onConfirm={() => close.mutate()}
          error={close.isError && <Alert tone="error">{errorMessage(close.error)}</Alert>}
        >
          <p>
            Ngày <strong>{date.split('-').reverse().join('/')}</strong>: sổ sách {formatCash(day.data.expectedCash)}, đếm được{' '}
            <strong>{formatCash(countedCash)}</strong>
            {difference ? ` (${difference > 0 ? 'thừa' : 'thiếu'} ${formatCash(Math.abs(difference))})` : ' — khớp'}.
          </p>
        </ConfirmDialog>
      )}
    </div>
  );
}

function ClosedCard({ closed }: { closed: ClosedReconciliation }) {
  return (
    <div className="space-y-2 rounded-xl bg-slate-50 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={OUTCOME_TONE[closed.outcome]}>{RECONCILIATION_OUTCOME_LABELS[closed.outcome]}</Badge>
        <span className="text-sm text-slate-600">Đã chốt bởi {closed.closedByName} lúc {formatDateTime(closed.closedAt)}</span>
      </div>
      <p className="text-sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
        Đếm được <strong>{formatCash(closed.countedCash)}</strong> / sổ sách {formatCash(closed.expectedCash)} — chênh lệch{' '}
        <strong>{signedCash(closed.discrepancy)}</strong>
      </p>
      {closed.note && <p className="text-sm text-slate-700">Giải trình: {closed.note}</p>}
    </div>
  );
}
