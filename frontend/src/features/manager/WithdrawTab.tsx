import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api, errorMessage, type Idempotent } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { ConfirmDialog } from '../../shared/components/Dialog';
import { TextField } from '../../shared/components/Field';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { useIdempotencyKey } from '../../shared/hooks/useIdempotencyKey';
import type { WithdrawalResult } from '../../shared/types/api';
import { formatCash, formatDateTime, formatNumber, formatPoints } from '../../shared/utils/format';
import { amountSchema, parseAmount } from './amount';
import { Receipt } from './Receipt';

const schema = z.object({ points: amountSchema('điểm') });

/** QL02 — rút điểm của chủ tài khoản đổi ra tiền mặt. Ví nguồn luôn là ví chủ tài khoản (không gửi lên). */
export function WithdrawTab() {
  const queryClient = useQueryClient();
  const owner = useQuery({ queryKey: queryKeys.ownerWallet, queryFn: api.ownerWallet, staleTime: 0 });
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { points: '' },
  });
  const idempotency = useIdempotencyKey();
  const [points, setPoints] = useState<number | null>(null);
  const [receipt, setReceipt] = useState<Idempotent<WithdrawalResult> | null>(null);

  const withdraw = useMutation({
    mutationFn: ({ amount, key }: { amount: number; key: string }) => api.withdraw(amount, key),
    onSuccess: async (result) => {
      idempotency.finish();
      setPoints(null);
      form.reset();
      await queryClient.invalidateQueries({ queryKey: queryKeys.ownerWallet });
      setReceipt(result);
    },
  });

  function cancel() {
    setPoints(null);
    idempotency.finish();
    withdraw.reset();
  }

  if (receipt) {
    const result = receipt.data;
    return (
      <Receipt title="Rút điểm thành công" replayed={receipt.replayed} onDone={() => setReceipt(null)}>
        <InfoRow label="Số điểm đã rút" value={<span className="text-red-600">−{formatNumber(result.points)}</span>} />
        <InfoRow label="Tỷ lệ quy đổi" value={`${formatNumber(result.cashPerPoint)} đ = 1 điểm`} />
        <InfoRow label="Tiền mặt cần trả" value={<span className="text-xl font-bold text-emerald-700">{formatCash(result.cashAmount)}</span>} />
        <InfoRow label="Số dư trước" value={formatPoints(result.balanceBefore)} />
        <InfoRow label="Số dư sau" value={<span className="text-lg font-bold">{formatPoints(result.balanceAfter)}</span>} />
        <InfoRow label="Thời gian" value={formatDateTime(result.createdAt)} />
        <InfoRow label="Mã giao dịch" value={`#${result.transactionId}`} />
      </Receipt>
    );
  }

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <Card title="Ví chủ tài khoản">
        {owner.isPending && <LoadingBlock />}
        {owner.isError && <QueryError error={owner.error} onRetry={() => void owner.refetch()} />}
        {owner.data && (
          <dl>
            <InfoRow label="Chủ tài khoản" value={owner.data.fullName} />
            <InfoRow label="Số điểm hiện có" value={<span className="text-2xl font-bold">{formatPoints(owner.data.balance)}</span>} />
          </dl>
        )}
      </Card>

      <Card title="Rút điểm">
        <form
          className="space-y-4"
          noValidate
          onSubmit={form.handleSubmit((values) => {
            setPoints(parseAmount(values.points));
            idempotency.start(); // mở bước xác nhận -> key mới cho giao dịch này
          })}
        >
          <TextField
            label="Số điểm cần rút"
            inputMode="numeric"
            placeholder="2000"
            error={form.formState.errors.points?.message}
            {...form.register('points')}
          />
          <Button type="submit" className="w-full" size="lg" disabled={!owner.data}>
            Rút điểm
          </Button>
        </form>
      </Card>

      {points !== null && idempotency.key && (
        <ConfirmDialog
          title="Xác nhận rút điểm"
          confirmLabel="Xác nhận rút"
          tone="danger"
          pending={withdraw.isPending}
          onCancel={cancel}
          onConfirm={() => idempotency.key && withdraw.mutate({ amount: points, key: idempotency.key })}
          error={withdraw.isError && <Alert tone="error">{errorMessage(withdraw.error)}</Alert>}
        >
          <dl>
            <InfoRow label="Chủ tài khoản" value={owner.data?.fullName ?? '—'} />
            <InfoRow label="Số điểm rút" value={<span className="text-lg font-bold">{formatPoints(points)}</span>} />
            {owner.data && <InfoRow label="Số điểm hiện có" value={formatPoints(owner.data.balance)} />}
          </dl>
          <p className="text-sm text-slate-500">Số tiền mặt phải trả do hệ thống tính và hiện trên biên nhận.</p>
        </ConfirmDialog>
      )}
    </div>
  );
}
