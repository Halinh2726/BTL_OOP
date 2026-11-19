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
import { useIdempotencyKey } from '../../shared/hooks/useIdempotencyKey';
import type { PassOption } from '../../shared/types/api';
import { formatDay, formatPlate, formatPoints } from '../../shared/utils/format';

type Choice = { option: PassOption; month: string };

/**
 * Đợt 5 — vé tháng: trả điểm một lần, cả tháng gửi xe miễn phí (xe ra không bị trừ, xe vào không cần điểm tối thiểu).
 * Mua cho tháng này hoặc tháng sau; mỗi xe một vé mỗi tháng. Bấm "Mua" hai lần cũng chỉ trừ điểm một lần.
 */
export function MonthlyPassCard() {
  const queryClient = useQueryClient();
  const idempotency = useIdempotencyKey();
  const [choice, setChoice] = useState<Choice | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const overview = useQuery({ queryKey: queryKeys.myPasses, queryFn: api.myPasses });

  const buy = useMutation({
    mutationFn: ({ option, month }: Choice) => api.buyPass({ vehicleId: option.vehicleId, month }, idempotency.key ?? ''),
    onSuccess: async ({ data }) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.myPasses }),
        queryClient.invalidateQueries({ queryKey: queryKeys.myWallet }),
        queryClient.invalidateQueries({ queryKey: queryKeys.myTransactionsAll }),
        queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
      ]);
      idempotency.finish();
      setChoice(null);
      setMessage(`Đã mua vé tháng ${formatDay(data.pass.month)} — còn ${formatPoints(data.balanceAfter)}.`);
    },
  });

  function open(option: PassOption, month: string) {
    buy.reset();
    idempotency.start();
    setChoice({ option, month });
  }

  return (
    <Card title="Vé tháng">
      {overview.isPending && <LoadingBlock />}
      {overview.isError && <QueryError error={overview.error} onRetry={() => void overview.refetch()} />}
      {overview.data && (
        <div className="space-y-4">
          <p className="text-sm text-slate-600">
            Trả điểm một lần, gửi xe miễn phí cả tháng: xe ra không bị trừ phí, xe vào không cần số điểm tối thiểu.
          </p>
          {message && <Alert tone="success">{message}</Alert>}
          {overview.data.options.length === 0 && <p className="text-sm text-slate-500">Bạn chưa có xe nào đang sử dụng.</p>}
          <ul className="space-y-2">
            {overview.data.options.map((option) => (
              <li key={option.vehicleId} className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3">
                <div>
                  <p className="font-mono text-lg font-semibold">{formatPlate(option.plateNumber)}</p>
                  <p className="text-sm text-slate-500">
                    {option.vehicleTypeName} · {option.price === null ? 'chưa bán vé tháng' : `${formatPoints(option.price)} / tháng`}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  {[
                    { month: overview.data.currentMonth, pass: option.current },
                    { month: overview.data.nextMonth, pass: option.next },
                  ].map(({ month, pass }) =>
                    pass ? (
                      <Badge key={month} tone="green">Có vé {formatDay(month)} · đến {formatDay(pass.validUntil)}</Badge>
                    ) : (
                      <Button key={month} size="sm" variant="secondary" disabled={option.price === null} onClick={() => open(option, month)}>
                        Mua vé {formatDay(month)}
                      </Button>
                    ),
                  )}
                </div>
              </li>
            ))}
          </ul>
          {overview.data.passes.length > 0 && (
            <details className="rounded-xl ring-1 ring-slate-200">
              <summary className="cursor-pointer select-none px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50">
                Vé đã mua ({overview.data.passes.length})
              </summary>
              <ul className="divide-y divide-slate-100 border-t border-slate-200 text-sm">
                {overview.data.passes.map((p) => (
                  <li key={p.id} className="flex justify-between gap-2 px-4 py-2">
                    <span><span className="font-mono">{p.plateNumber ? formatPlate(p.plateNumber) : '—'}</span> · tháng {formatDay(p.month)}</span>
                    <span className="text-slate-600">{formatPoints(p.price)} · GD #{p.transactionId}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </div>
      )}
      {choice && (
        <ConfirmDialog
          title={`Mua vé tháng ${formatDay(choice.month)}`}
          confirmLabel={`Mua — trừ ${formatPoints(choice.option.price ?? 0)}`}
          tone="success"
          pending={buy.isPending}
          onCancel={() => { setChoice(null); idempotency.finish(); }}
          onConfirm={() => buy.mutate(choice)}
          error={buy.isError && <Alert tone="error">{errorMessage(buy.error)}</Alert>}
        >
          <p>
            Xe <strong className="font-mono">{formatPlate(choice.option.plateNumber)}</strong> được gửi miễn phí từ nay đến hết tháng{' '}
            {formatDay(choice.month)}. Vé đã mua không hoàn lại.
          </p>
        </ConfirmDialog>
      )}
    </Card>
  );
}
