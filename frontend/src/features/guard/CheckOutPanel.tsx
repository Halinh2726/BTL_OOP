import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { useIdempotencyKey } from '../../shared/hooks/useIdempotencyKey';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import { formatDateTime, formatPoints } from '../../shared/utils/format';
import { PlateLookupForm } from './PlateLookupForm';
import { SuccessPanel } from './SuccessPanel';
import { usePlateFocus } from './usePlateFocus';
import { VerdictBanner } from './VerdictBanner';

/**
 * BV02 — xe ra: tra cứu (kèm phí tạm tính) -> xác nhận -> backend tự trừ điểm sinh viên, cộng cho chủ tài khoản.
 * Phí hiển thị là của backend; số trừ thật lấy từ kết quả xác nhận.
 */
export function CheckOutPanel() {
  const { inputRef, formKey, resetForm } = usePlateFocus();
  const typeName = useVehicleTypeName();
  const idempotency = useIdempotencyKey();
  const preview = useMutation({
    mutationFn: api.previewCheckOut,
    // Bước xác nhận mở ra -> sinh Idempotency-Key cho lượt thanh toán này
    onSuccess: (result) => (result.allowed ? idempotency.start() : idempotency.finish()),
  });
  const checkOut = useMutation({
    mutationFn: ({ sessionId, key }: { sessionId: number; key: string }) => api.checkOut(sessionId, key),
    onSuccess: idempotency.finish,
  });

  function lookup(plate: string) {
    checkOut.reset();
    preview.mutate(plate);
  }

  function nextVehicle() {
    preview.reset();
    checkOut.reset();
    idempotency.finish();
    resetForm();
  }

  if (checkOut.data) {
    const result = checkOut.data.data;
    return (
      <SuccessPanel
        title="Đã cho xe ra và thanh toán"
        onNext={nextVehicle}
        notice={
          checkOut.data.replayed && <Alert tone="info">Lượt này đã được thanh toán trước đó — không trừ điểm lần hai.</Alert>
        }
      >
        <InfoRow label="Phí đã trừ" value={<span className="text-red-600">{formatPoints(result.fee)}</span>} />
        <InfoRow label="Điểm còn lại của sinh viên" value={formatPoints(result.balanceAfter)} />
        <InfoRow label="Giờ ra" value={formatDateTime(result.exitedAt)} />
        <InfoRow label="Mã giao dịch" value={`#${result.transactionId}`} />
      </SuccessPanel>
    );
  }

  const data = preview.data;
  const canConfirm = data?.allowed === true && data.sessionId !== null && idempotency.key !== null;
  return (
    <div className="space-y-4">
      <Card>
        <PlateLookupForm key={formKey} ref={inputRef} onLookup={lookup} pending={preview.isPending} disabled={checkOut.isPending} />
      </Card>
      {preview.isError && <Alert tone="error">{errorMessage(preview.error)}</Alert>}
      {data && (
        <Card>
          <div className="space-y-4">
            <VerdictBanner allowed={data.allowed} allowedText="Cho xe ra" reason={data.reason} />
            {data.fullName !== null && (
              <dl className="text-lg">
                <InfoRow label="Biển số" value={<span className="font-mono">{data.plateNumber}</span>} />
                <InfoRow label="Sinh viên" value={data.fullName} />
                <InfoRow label="MSSV" value={data.studentCode} />
                <InfoRow label="Loại xe" value={typeName(data.vehicleTypeCode)} />
                <InfoRow label="Giờ vào" value={data.enteredAt ? formatDateTime(data.enteredAt) : '—'} />
                <InfoRow label="Số điểm hiện có" value={data.balance === null ? '—' : formatPoints(data.balance)} />
                <InfoRow
                  label="Phí gửi xe"
                  value={data.fee === null ? '—' : <span className="text-xl font-bold text-red-600">{formatPoints(data.fee)}</span>}
                />
              </dl>
            )}
            {checkOut.isError && <Alert tone="error">{errorMessage(checkOut.error)}</Alert>}
            <div className="flex flex-col gap-3 sm:flex-row">
              <Button variant="secondary" size="xl" className="sm:w-1/3" onClick={nextVehicle} disabled={checkOut.isPending}>
                Hủy
              </Button>
              <Button
                variant="success"
                size="xl"
                className="flex-1"
                disabled={!canConfirm}
                loading={checkOut.isPending}
                onClick={() => {
                  // Bấm lại sau lỗi mạng: GIỮ NGUYÊN key -> backend không trừ điểm hai lần
                  if (data.sessionId !== null && idempotency.key) {
                    checkOut.mutate({ sessionId: data.sessionId, key: idempotency.key });
                  }
                }}
              >
                Xác nhận cho xe ra
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
