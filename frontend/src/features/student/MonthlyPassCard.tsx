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

type Choice = { option: PassOption; month: string; monthsCount: number; total: number };

const MONTH_CHOICES = [1, 2, 3, 4, 5, 6, 9, 12];

/**
 * Vé tháng: mua cho 1 tháng (mặc định) hoặc chọn mua liên tiếp nhiều tháng (tính nguyên giá, không ưu đãi);
 * hiển thị số ngày còn lại của vé đang hiệu lực và cảnh báo khi sắp hết hạn.
 */
export function MonthlyPassCard() {
  const queryClient = useQueryClient();
  const idempotency = useIdempotencyKey();
  const [monthsCount, setMonthsCount] = useState<number>(1);
  const [choice, setChoice] = useState<Choice | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const overview = useQuery({ queryKey: queryKeys.myPasses, queryFn: api.myPasses });

  const buy = useMutation({
    mutationFn: ({ option, month, monthsCount: count }: Choice) =>
      api.buyPass({ vehicleId: option.vehicleId, month, monthsCount: count }, idempotency.key ?? ''),
    onSuccess: async ({ data }) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.myPasses }),
        queryClient.invalidateQueries({ queryKey: queryKeys.myWallet }),
        queryClient.invalidateQueries({ queryKey: queryKeys.myTransactionsAll }),
        queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
      ]);
      const count = data.monthsCount ?? 1;
      idempotency.finish();
      setChoice(null);
      setMessage(
        count > 1 && data.validUntil
          ? `Đã mua vé ${count} tháng (hết hạn ${formatDay(data.validUntil)}) — còn ${formatPoints(data.balanceAfter)}.`
          : `Đã mua vé tháng ${formatDay(data.pass.month)} — còn ${formatPoints(data.balanceAfter)}.`,
      );
    },
  });

  function open(option: PassOption, month: string) {
    buy.reset();
    idempotency.start();
    const total = (option.price ?? 0) * monthsCount;
    setChoice({ option, month, monthsCount, total });
  }

  return (
    <Card title="Vé tháng">
      {overview.isPending && <LoadingBlock />}
      {overview.isError && <QueryError error={overview.error} onRetry={() => void overview.refetch()} />}
      {overview.data && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-slate-600">
              Trả điểm một lần, gửi xe miễn phí cả tháng: xe ra không bị trừ phí, xe vào không cần số điểm tối thiểu.
            </p>
            <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
              Số tháng muốn mua
              <select
                value={monthsCount}
                onChange={(e) => setMonthsCount(Number(e.target.value))}
                className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-semibold text-slate-900"
              >
                {MONTH_CHOICES.map((count) => (
                  <option key={count} value={count}>
                    {count} tháng
                  </option>
                ))}
              </select>
            </label>
          </div>

          {message && <Alert tone="success">{message}</Alert>}
          {overview.data.options.length === 0 && <p className="text-sm text-slate-500">Bạn chưa có xe nào đang sử dụng.</p>}
          <ul className="space-y-2">
            {overview.data.options.map((option) => {
              const total = option.price === null ? 0 : option.price * monthsCount;
              const expiringSoon = option.remainingDays !== null && option.remainingDays <= 5;
              return (
                <li key={option.vehicleId} className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3">
                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="font-mono text-lg font-semibold">{formatPlate(option.plateNumber)}</p>
                      {option.activeValidUntil && option.remainingDays !== null && (
                        <Badge tone={expiringSoon ? 'amber' : 'green'}>
                          {expiringSoon ? `Sắp hết hạn (${option.remainingDays} ngày)` : `Hiệu lực còn ${option.remainingDays} ngày`} · đến {formatDay(option.activeValidUntil)}
                        </Badge>
                      )}
                    </div>
                    <p className="text-sm text-slate-500">
                      {option.vehicleTypeName} · {option.price === null ? 'chưa bán vé tháng' : (
                        <>
                          {formatPoints(option.price)} / tháng
                          {monthsCount > 1 && (
                            <> · <strong className="text-slate-800">{monthsCount} tháng: {formatPoints(total)}</strong></>
                          )}
                        </>
                      )}
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
                          {monthsCount > 1 ? `Mua ${monthsCount} tháng từ ${formatDay(month)}` : `Mua vé ${formatDay(month)}`}
                        </Button>
                      ),
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
          {overview.data.passes.length > 0 && (
            <details className="rounded-xl ring-1 ring-slate-200">
              <summary className="cursor-pointer select-none px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50">
                Vé đã mua ({overview.data.passes.length})
              </summary>
              <ul className="divide-y divide-slate-100 border-t border-slate-200 text-sm">
                {overview.data.passes.map((p) => (
                  <li key={p.id} className="flex justify-between gap-2 px-4 py-2">
                    <span>
                      <span className="font-mono">{p.plateNumber ? formatPlate(p.plateNumber) : '—'}</span> · tháng {formatDay(p.month)}
                    </span>
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
          title={choice.monthsCount > 1
            ? `Mua vé ${choice.monthsCount} tháng từ ${formatDay(choice.month)}`
            : `Mua vé tháng ${formatDay(choice.month)}`}
          confirmLabel={`Mua — trừ ${formatPoints(choice.total)}`}
          tone="success"
          pending={buy.isPending}
          onCancel={() => { setChoice(null); idempotency.finish(); }}
          onConfirm={() => buy.mutate(choice)}
          error={buy.isError && <Alert tone="error">{errorMessage(buy.error)}</Alert>}
        >
          <p className="text-sm">
            Xe <strong className="font-mono">{formatPlate(choice.option.plateNumber)}</strong> được gửi miễn phí trong{' '}
            <strong>{choice.monthsCount} tháng</strong> bắt đầu từ tháng {formatDay(choice.month)} (đơn giá{' '}
            {formatPoints(choice.option.price ?? 0)} / tháng). Vé đã mua không hoàn lại.
          </p>
        </ConfirmDialog>
      )}
    </Card>
  );
}
