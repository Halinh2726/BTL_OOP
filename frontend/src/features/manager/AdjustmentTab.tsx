import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api, errorMessage, type Idempotent } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { SelectField, TextField } from '../../shared/components/Field';
import { newIdempotencyKey } from '../../shared/hooks/useIdempotencyKey';
import type { AdjustResult, AdjustmentDirection } from '../../shared/types/api';
import { formatDateTime, formatNumber, formatPoints } from '../../shared/utils/format';
import { parseAmount } from './amount';
import { Receipt } from './Receipt';

const DIRECTIONS: Record<AdjustmentDirection, string> = {
  HOAN_DIEM: 'Hoàn điểm cho sinh viên (ví chủ bãi → ví sinh viên)',
  THU_HOI: 'Thu hồi điểm đã hoàn nhầm (ví sinh viên → ví chủ bãi)',
};

/**
 * Giao dịch điều chỉnh (Đợt 3) — sửa sai sót mà KHÔNG sửa giao dịch cũ (R10): trừ phí hai lần, khiếu nại đúng, bồi thường
 * chủ bãi xe đã quyết định. Bắt buộc ghi lý do; nên ghi mã giao dịch gốc để truy vết.
 */
export function AdjustmentTab() {
  const [studentCode, setStudentCode] = useState('');
  const [direction, setDirection] = useState<AdjustmentDirection>('HOAN_DIEM');
  const [points, setPoints] = useState('');
  const [reason, setReason] = useState('');
  const [original, setOriginal] = useState('');
  const [receipt, setReceipt] = useState<Idempotent<AdjustResult> | null>(null);
  /** Một Idempotency-Key cho MỘT nội dung điều chỉnh: bấm lại sau lỗi mạng dùng lại key; sửa nội dung thì key mới. */
  const keyRef = useRef<string | null>(null);
  const code = studentCode.trim().toUpperCase();
  useEffect(() => {
    keyRef.current = null;
  }, [code, direction, points, reason, original]);
  const student = useQuery({
    queryKey: queryKeys.student(code),
    queryFn: () => api.lookupStudent(code),
    enabled: code.length >= 3,
    retry: false,
  });

  const adjust = useMutation({
    mutationFn: (key: string) =>
      api.adjustPoints(
        {
          studentCode: code,
          direction,
          points: parseAmount(points) ?? 0,
          reason: reason.trim(),
          originalTransactionId: original.trim() ? Number(original.replace(/\D/g, '')) : undefined,
        },
        key,
      ),
    onSuccess: (result) => {
      keyRef.current = null;
      setReceipt(result);
    },
  });

  const pointsValue = parseAmount(points);
  const valid = code.length > 0 && pointsValue !== null && pointsValue > 0 && reason.trim().length > 0;

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!valid) return;
    keyRef.current ??= newIdempotencyKey();
    adjust.mutate(keyRef.current);
  }

  if (receipt) {
    const data = receipt.data;
    return (
      <Receipt
        title="Đã ghi giao dịch điều chỉnh"
        replayed={receipt.replayed}
        onDone={() => {
          setReceipt(null);
          setPoints('');
          setReason('');
          setOriginal('');
        }}
      >
        <InfoRow label="Mã giao dịch" value={`#${data.transactionId}`} />
        <InfoRow label="Sinh viên" value={`${data.studentName} (${data.studentCode})`} />
        <InfoRow label="Loại" value={data.direction === 'HOAN_DIEM' ? 'Hoàn điểm cho sinh viên' : 'Thu hồi điểm'} />
        <InfoRow label="Số điểm" value={formatPoints(data.points)} />
        <InfoRow label="Lý do" value={data.reason} />
        {data.originalTransactionId && <InfoRow label="Giao dịch gốc" value={`#${data.originalTransactionId}`} />}
        <InfoRow label="Thời điểm" value={formatDateTime(data.createdAt)} />
      </Receipt>
    );
  }

  return (
    <Card title="Giao dịch điều chỉnh">
      <form className="space-y-4" onSubmit={submit} noValidate>
        <Alert tone="info">
          Giao dịch đã ghi không sửa / xóa được (R10). Sai sót được sửa bằng một giao dịch điều chỉnh MỚI chuyển điểm giữa ví
          chủ bãi xe và ví sinh viên — tổng điểm hệ thống không đổi.
        </Alert>
        <TextField label="MSSV" className="font-mono uppercase" value={studentCode} onChange={(event) => setStudentCode(event.target.value)} />
        {student.data && (
          <p className="text-sm text-slate-600">
            {student.data.fullName} · hiện có {formatPoints(student.data.balance)}
          </p>
        )}
        {student.isError && code.length >= 3 && <p className="text-sm text-red-600">{errorMessage(student.error)}</p>}
        <SelectField label="Loại điều chỉnh" value={direction} onChange={(event) => setDirection(event.target.value as AdjustmentDirection)}>
          {(Object.keys(DIRECTIONS) as AdjustmentDirection[]).map((key) => (
            <option key={key} value={key}>
              {DIRECTIONS[key]}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Số điểm"
          inputMode="numeric"
          value={points}
          onChange={(event) => setPoints(event.target.value)}
          hint={pointsValue ? formatPoints(pointsValue) : undefined}
        />
        <div className="space-y-1">
          <label htmlFor="adjust-reason" className="block text-sm font-medium text-slate-700">
            Lý do (bắt buộc)
          </label>
          <textarea
            id="adjust-reason"
            rows={3}
            maxLength={500}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            placeholder="Ví dụ: sinh viên bị trừ phí hai lần lượt gửi #41 (khiếu nại đã xác minh)"
            className="block w-full rounded-lg border border-slate-300 px-3 py-2"
          />
        </div>
        <TextField
          label="Mã giao dịch gốc (nếu có)"
          inputMode="numeric"
          placeholder="Ví dụ: 41"
          value={original}
          onChange={(event) => setOriginal(event.target.value)}
          hint={original ? `#${formatNumber(Number(original.replace(/\D/g, '')) || 0)}` : 'Xem trong lịch sử giao dịch của sinh viên'}
        />
        {adjust.isError && <Alert tone="error">{errorMessage(adjust.error)}</Alert>}
        <Button type="submit" disabled={!valid} loading={adjust.isPending}>
          Ghi giao dịch điều chỉnh
        </Button>
      </form>
    </Card>
  );
}
