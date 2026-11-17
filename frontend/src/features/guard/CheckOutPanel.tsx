import { useMutation } from '@tanstack/react-query';
import { useRef } from 'react';
import { api, errorMessage } from '../../shared/api';
import { ApiError } from '../../shared/api/ApiError';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { newIdempotencyKey, useIdempotencyKey } from '../../shared/hooks/useIdempotencyKey';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import { formatDateTime, formatPoints } from '../../shared/utils/format';
import { playErrorSound, playSuccessSound } from '../../shared/utils/sound';
import { PlateLookupForm } from './PlateLookupForm';
import { SuccessPanel } from './SuccessPanel';
import { usePlateFocus } from './usePlateFocus';
import { VerdictBanner } from './VerdictBanner';
import { VietQrPaymentCard } from './VietQrPaymentCard';

/**
 * BV02 — Xe ra:
 * 1. Chế độ 1 chạm (One-Scan Flow như vé bus): Giơ mã QR/biển số -> tự động trừ điểm và cho xe ra ngay.
 * 2. Chế độ trực tuyến VietQR: Khi ví sinh viên không đủ điểm -> tự động tạo đơn gửi xe và hiển thị mã VietQR Napas247.
 * 3. Chế độ thủ công: Tra cứu trước (kèm phí tạm tính) -> xác nhận -> backend trừ điểm.
 */
export function CheckOutPanel() {
  const { inputRef, formKey, resetForm } = usePlateFocus();
  const typeName = useVehicleTypeName();
  const idempotency = useIdempotencyKey();
  const lastPlateRef = useRef<string>('');

  const preview = useMutation({
    mutationFn: api.previewCheckOut,
    // Bước xác nhận mở ra -> sinh Idempotency-Key cho lượt thanh toán này
    onSuccess: (result) => (result.allowed || result.vietQrOrder ? idempotency.start() : idempotency.finish()),
  });

  const checkOut = useMutation({
    mutationFn: ({ sessionId, key }: { sessionId: number; key: string }) => api.checkOut(sessionId, key),
    onSuccess: () => {
      idempotency.finish();
      playSuccessSound();
    },
    onError: () => playErrorSound(),
  });

  const quickCheckOut = useMutation({
    mutationFn: ({ plate, key }: { plate: string; key: string }) => api.quickCheckOut(plate, key),
    onSuccess: () => {
      idempotency.finish();
      playSuccessSound();
    },
    onError: (err) => {
      playErrorSound();
      // Nếu không đủ điểm ví khi quét 1 chạm -> tự động chuyển sang tra cứu để hiển thị đơn VietQR
      if (err instanceof ApiError && err.code === 'INSUFFICIENT_POINTS' && lastPlateRef.current) {
        preview.mutate(lastPlateRef.current);
      }
    },
  });

  const payVietQr = useMutation({
    mutationFn: ({ sessionId, key }: { sessionId: number; key: string }) => api.payVietQr(sessionId, key),
    onSuccess: () => {
      idempotency.finish();
      playSuccessSound();
    },
    onError: () => playErrorSound(),
  });

  function lookup(plate: string) {
    lastPlateRef.current = plate;
    checkOut.reset();
    quickCheckOut.reset();
    payVietQr.reset();
    preview.mutate(plate);
  }

  function handleQuickScan(plate: string) {
    lastPlateRef.current = plate;
    preview.reset();
    checkOut.reset();
    quickCheckOut.reset();
    payVietQr.reset();
    const key = idempotency.key ?? newIdempotencyKey();
    quickCheckOut.mutate({ plate, key });
  }

  function nextVehicle() {
    lastPlateRef.current = '';
    preview.reset();
    checkOut.reset();
    quickCheckOut.reset();
    payVietQr.reset();
    idempotency.finish();
    resetForm();
  }

  // Kết quả thanh toán 1 chạm (trừ ví điểm)
  if (quickCheckOut.data) {
    const result = quickCheckOut.data.data;
    return (
      <SuccessPanel
        title="Đã thanh toán 1 chạm & Cho xe ra"
        onNext={nextVehicle}
        notice={
          quickCheckOut.data.replayed && <Alert tone="info">Lượt này đã được thanh toán trước đó — không trừ điểm lần hai.</Alert>
        }
      >
        <InfoRow label="Biển số" value={<span className="font-mono text-2xl font-bold tracking-wider">{result.plateNumber}</span>} />
        <InfoRow label="Phí đã trừ" value={<span className="text-2xl font-bold text-red-600">−{formatPoints(result.fee)}</span>} />
        <InfoRow label="Sinh viên" value={`${result.fullName} (${result.studentCode})`} />
        <InfoRow label="Loại xe" value={typeName(result.vehicleTypeCode)} />
        <InfoRow label="Điểm còn lại của sinh viên" value={formatPoints(result.balanceAfter)} />
        <InfoRow label="Giờ ra" value={formatDateTime(result.exitedAt)} />
        <InfoRow label="Mã giao dịch" value={`#${result.transactionId}`} />
      </SuccessPanel>
    );
  }

  // Kết quả thanh toán chuyển khoản qua VietQR
  if (payVietQr.data) {
    const result = payVietQr.data.data;
    return (
      <SuccessPanel
        title="Đã thanh toán VietQR & Cho xe ra"
        onNext={nextVehicle}
        notice={
          payVietQr.data.replayed && <Alert tone="info">Lượt này đã được thanh toán trước đó — không trừ điểm lần hai.</Alert>
        }
      >
        <InfoRow label="Biển số" value={<span className="font-mono text-2xl font-bold tracking-wider">{result.plateNumber}</span>} />
        <InfoRow
          label="Hình thức thanh toán"
          value={<span className="font-bold text-emerald-600">Chuyển khoản VietQR (Napas247)</span>}
        />
        <InfoRow label="Phí gửi xe" value={<span className="text-2xl font-bold text-red-600">−{formatPoints(result.fee)}</span>} />
        <InfoRow label="Sinh viên" value={`${result.fullName} (${result.studentCode})`} />
        <InfoRow label="Loại xe" value={typeName(result.vehicleTypeCode)} />
        <InfoRow label="Giờ ra" value={formatDateTime(result.exitedAt)} />
        <InfoRow label="Mã giao dịch" value={`#${result.transactionId}`} />
      </SuccessPanel>
    );
  }

  // Kết quả thanh toán sau khi xác nhận thủ công
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
        <InfoRow label="Phí đã trừ" value={<span className="text-red-600">−{formatPoints(result.fee)}</span>} />
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
        <PlateLookupForm
          key={formKey}
          ref={inputRef}
          onLookup={lookup}
          onQuickScan={handleQuickScan}
          quickScanLabel="Quét 1 chạm (Tự động trừ tiền)"
          pending={preview.isPending || quickCheckOut.isPending}
          disabled={checkOut.isPending || quickCheckOut.isPending || payVietQr.isPending}
          parkedOnly
          onEdit={() => {
            preview.reset();
            checkOut.reset();
            quickCheckOut.reset();
            payVietQr.reset();
            idempotency.finish();
          }}
        />
      </Card>
      {quickCheckOut.isError && <Alert tone="error">{errorMessage(quickCheckOut.error)}</Alert>}
      {preview.isError && <Alert tone="error">{errorMessage(preview.error)}</Alert>}

      {/* Khi không đủ điểm ví: hiển thị trực tiếp thẻ VietQR để thanh toán online */}
      {data?.vietQrOrder && (
        <VietQrPaymentCard
          order={data.vietQrOrder}
          confirming={payVietQr.isPending}
          error={payVietQr.isError ? errorMessage(payVietQr.error) : null}
          onCancel={nextVehicle}
          onConfirm={() => {
            if (data.vietQrOrder) {
              const key = idempotency.key ?? newIdempotencyKey();
              payVietQr.mutate({ sessionId: data.vietQrOrder.sessionId, key });
            }
          }}
        />
      )}

      {/* Thông tin tra cứu bình thường khi xe đủ điều kiện cho ra hoặc lỗi khác */}
      {data && !data.vietQrOrder && (
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
