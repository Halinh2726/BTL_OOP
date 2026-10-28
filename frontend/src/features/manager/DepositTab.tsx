import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
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
import type { DepositResult, StudentSummary } from '../../shared/types/api';
import { formatCash, formatDateTime, formatNumber, formatPoints } from '../../shared/utils/format';
import { amountSchema, parseAmount } from './amount';
import { Receipt } from './Receipt';

/** QL01 — sinh viên nộp tiền mặt, quản lý đổi sang điểm. */
export function DepositTab() {
  const [studentCode, setStudentCode] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<Idempotent<DepositResult> | null>(null);
  const student = useQuery({
    queryKey: queryKeys.student(studentCode ?? ''),
    queryFn: () => api.lookupStudent(studentCode ?? ''),
    enabled: studentCode !== null,
    staleTime: 0,
  });

  if (receipt) {
    const result = receipt.data;
    return (
      <Receipt
        title="Đổi điểm thành công"
        replayed={receipt.replayed}
        onDone={() => {
          setReceipt(null);
          setStudentCode(null);
        }}
      >
        <InfoRow label="MSSV" value={result.studentCode} />
        <InfoRow label="Tiền mặt đã nhận" value={formatCash(result.cashAmount)} />
        <InfoRow label="Tỷ lệ quy đổi" value={`${formatNumber(result.cashPerPoint)} đ = 1 điểm`} />
        <InfoRow label="Điểm được cộng" value={<span className="text-emerald-700">+{formatNumber(result.points)}</span>} />
        <InfoRow label="Số dư trước" value={formatPoints(result.balanceBefore)} />
        <InfoRow label="Số dư sau" value={<span className="text-lg font-bold">{formatPoints(result.balanceAfter)}</span>} />
        <InfoRow label="Thời gian" value={formatDateTime(result.createdAt)} />
        <InfoRow label="Mã giao dịch" value={`#${result.transactionId}`} />
      </Receipt>
    );
  }

  return (
    <div className="grid gap-6 md:grid-cols-2">
      <Card title="1. Tìm sinh viên">
        <StudentSearch onSearch={setStudentCode} pending={student.isFetching} />
        <div className="mt-4">
          {student.isPending && student.fetchStatus === 'fetching' && <LoadingBlock />}
          {student.isError && <QueryError error={student.error} />}
          {student.data && (
            <dl>
              <InfoRow label="Họ tên" value={student.data.fullName} />
              <InfoRow label="MSSV" value={student.data.studentCode} />
              <InfoRow label="Số điểm hiện có" value={formatPoints(student.data.balance)} />
            </dl>
          )}
        </div>
      </Card>
      {student.data && <DepositForm key={student.data.studentCode} student={student.data} onDone={setReceipt} />}
    </div>
  );
}

function StudentSearch({ onSearch, pending }: { onSearch: (code: string) => void; pending: boolean }) {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    const code = value.trim().toUpperCase();
    if (!code) {
      setError('Vui lòng nhập MSSV');
      return;
    }
    setError(null);
    onSearch(code);
  }

  return (
    <form onSubmit={submit} noValidate className="flex items-start gap-2">
      <div className="flex-1">
        <TextField
          label="Mã sinh viên"
          placeholder="B21DCCN001"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          className="uppercase"
          error={error ?? undefined}
          autoFocus
        />
      </div>
      <Button type="submit" className="mt-6" loading={pending}>
        Tìm
      </Button>
    </form>
  );
}

const schema = z.object({ cashAmount: amountSchema('tiền') });

function DepositForm({ student, onDone }: { student: StudentSummary; onDone: (result: Idempotent<DepositResult>) => void }) {
  const queryClient = useQueryClient();
  const rate = useQuery({ queryKey: queryKeys.exchangeRate, queryFn: api.exchangeRate });
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { cashAmount: '' },
  });
  const idempotency = useIdempotencyKey();
  const [cashAmount, setCashAmount] = useState<number | null>(null);

  const deposit = useMutation({
    mutationFn: ({ amount, key }: { amount: number; key: string }) =>
      api.deposit({ studentCode: student.studentCode, cashAmount: amount }, key),
    onSuccess: async (result) => {
      idempotency.finish();
      await queryClient.invalidateQueries({ queryKey: queryKeys.student(student.studentCode) });
      onDone(result);
    },
  });

  const typed = parseAmount(form.watch('cashAmount'));
  const cashPerPoint = rate.data?.cashPerPoint;
  const estimate = typed !== null && cashPerPoint ? Math.floor(typed / cashPerPoint) : null;

  function cancel() {
    setCashAmount(null);
    idempotency.finish();
    deposit.reset();
  }

  return (
    <Card title="2. Nhập số tiền">
      <form
        className="space-y-4"
        noValidate
        onSubmit={form.handleSubmit((values) => {
          setCashAmount(parseAmount(values.cashAmount));
          idempotency.start(); // mở bước xác nhận -> key mới cho giao dịch này
        })}
      >
        <TextField
          label="Số tiền mặt sinh viên nộp (đ)"
          inputMode="numeric"
          placeholder="50000"
          autoFocus
          error={form.formState.errors.cashAmount?.message}
          hint={
            cashPerPoint
              ? `Tỷ lệ: ${formatNumber(cashPerPoint)} đ = 1 điểm${estimate !== null ? ` · sẽ cộng khoảng ${formatNumber(estimate)} điểm` : ''}`
              : undefined
          }
          {...form.register('cashAmount')}
        />
        {rate.isError && <QueryError error={rate.error} onRetry={() => void rate.refetch()} />}
        <Button type="submit" className="w-full" size="lg">
          Đổi điểm
        </Button>
      </form>

      {cashAmount !== null && idempotency.key && (
        <ConfirmDialog
          title="Xác nhận đổi điểm"
          confirmLabel="Xác nhận đã nhận tiền"
          tone="success"
          pending={deposit.isPending}
          onCancel={cancel}
          onConfirm={() => idempotency.key && deposit.mutate({ amount: cashAmount, key: idempotency.key })}
          error={deposit.isError && <Alert tone="error">{errorMessage(deposit.error)}</Alert>}
        >
          <dl>
            <InfoRow label="Sinh viên" value={student.fullName} />
            <InfoRow label="MSSV" value={student.studentCode} />
            <InfoRow label="Tiền mặt nhận" value={<span className="text-lg font-bold">{formatCash(cashAmount)}</span>} />
            {cashPerPoint && <InfoRow label="Điểm dự kiến cộng" value={formatPoints(Math.floor(cashAmount / cashPerPoint))} />}
          </dl>
          <p className="text-sm text-slate-500">Số điểm cộng chính thức do hệ thống tính và hiện trên biên nhận.</p>
        </ConfirmDialog>
      )}
    </Card>
  );
}
