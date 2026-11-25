import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { useIdempotencyKey } from '../../shared/hooks/useIdempotencyKey';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import { formatCash, formatDateTime, formatDay, formatPlate, formatPoints } from '../../shared/utils/format';
import { ContinuousScanPanel } from './continuous/ContinuousScanPanel';
import { checkOutFlow } from './continuous/gateFlows';
import { useGuardPreferences } from './guardPreferences';
import { PlateLookupForm } from './PlateLookupForm';
import { SuccessPanel } from './SuccessPanel';
import { usePlateFocus } from './usePlateFocus';
import { VerdictBanner } from './VerdictBanner';
import { VietQrPaymentCard } from './VietQrPaymentCard';

/**
 * BV02 — xe ra: tra cứu (kèm phí tạm tính) -> xác nhận trừ điểm, thu qua VietQR tại cổng hoặc thu tiền mặt.
 * Phí hiển thị do backend tính (đã gồm phí qua đêm nếu lấy từ ngày hôm sau và khóa sau giờ quy định).
 */
export function CheckOutPanel() {
  const { inputRef, formKey, resetForm } = usePlateFocus();
  const typeName = useVehicleTypeName();
  const { feedback, continuousScan } = useGuardPreferences();
  const pointKey = useIdempotencyKey();
  const cashKey = useIdempotencyKey();
  const vietQrKey = useIdempotencyKey();
  const [showVietQr, setShowVietQr] = useState(false);
  const queryClient = useQueryClient();

  const finishAllKeys = () => {
    pointKey.finish();
    cashKey.finish();
    vietQrKey.finish();
  };

  const preview = useMutation({
    mutationFn: api.previewCheckOut,
    // Bước xác nhận mở ra -> sinh Idempotency-Key riêng cho từng phương thức thanh toán
    onSuccess: (result) => {
      setShowVietQr(false);
      feedback(result.allowed ? 'allow' : 'deny');
      if (result.allowed) pointKey.start();
      else pointKey.finish();

      const canCashPay =
        result.sessionId !== null &&
        result.reason !== 'CHECKOUT_CURFEW' &&
        !result.passValidUntil &&
        (result.fee ?? 0) > 0;
      if (canCashPay) cashKey.start();
      else cashKey.finish();

      if (result.sessionId !== null && result.reason === 'INSUFFICIENT_POINTS' && result.vietQrOrder) {
        vietQrKey.start();
      } else {
        vietQrKey.finish();
      }
    },
  });

  const checkOut = useMutation({
    mutationFn: ({ sessionId, key }: { sessionId: number; key: string }) => api.checkOut(sessionId, key),
    onSuccess: () => {
      feedback('done');
      finishAllKeys();
      void queryClient.invalidateQueries({ queryKey: ['guard'] });
    },
  });

  const cashOut = useMutation({
    mutationFn: ({ sessionId, key }: { sessionId: number; key: string }) => api.checkOutCash(sessionId, key),
    onSuccess: () => {
      feedback('done');
      finishAllKeys();
      void queryClient.invalidateQueries({ queryKey: ['guard'] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.guardCashSheet });
    },
  });

  const vietQrOut = useMutation({
    mutationFn: ({ sessionId, key }: { sessionId: number; key: string }) => api.payVietQr(sessionId, key),
    onSuccess: () => {
      feedback('done');
      setShowVietQr(false);
      finishAllKeys();
      void queryClient.invalidateQueries({ queryKey: ['guard'] });
    },
  });

  function clearMutations() {
    checkOut.reset();
    cashOut.reset();
    vietQrOut.reset();
    setShowVietQr(false);
    finishAllKeys();
  }

  function lookup(plate: string) {
    clearMutations();
    preview.mutate(plate);
  }

  function nextVehicle() {
    preview.reset();
    clearMutations();
    resetForm();
  }

  if (continuousScan) {
    return <ContinuousScanPanel flow={checkOutFlow} />;
  }

  if (checkOut.data) {
    const result = checkOut.data.data;
    const free = result.transactionId === null;
    return (
      <SuccessPanel
        title={free ? 'Đã cho xe ra — miễn phí (vé tháng)' : 'Đã cho xe ra và thanh toán'}
        onNext={nextVehicle}
        notice={
          checkOut.data.replayed && <Alert tone="info">Lượt này đã được thanh toán trước đó — không trừ điểm lần hai.</Alert>
        }
      >
        {free ? (
          <InfoRow label="Vé tháng" value={<span className="text-emerald-700">Còn hiệu lực đến {formatDay(result.passValidUntil ?? '')}</span>} />
        ) : (
          <InfoRow label="Phí đã trừ" value={<span className="text-red-600">{formatPoints(result.fee)}</span>} />
        )}
        <InfoRow label="Điểm còn lại của sinh viên" value={formatPoints(result.balanceAfter)} />
        <InfoRow label="Giờ ra" value={formatDateTime(result.exitedAt)} />
        {!free && <InfoRow label="Mã giao dịch" value={`#${result.transactionId}`} />}
      </SuccessPanel>
    );
  }

  if (cashOut.data) {
    const result = cashOut.data.data;
    return (
      <SuccessPanel
        title="Đã thu tiền mặt và cho xe ra"
        onNext={nextVehicle}
        notice={
          <>
            {cashOut.data.replayed && (
              <Alert tone="info">Lượt thu tiền mặt này đã được ghi nhận trước đó — không thu hai lần.</Alert>
            )}
            <Alert tone="info">
              Đã ghi nhận vào bảng kê tiền mặt ca trực (không cộng vào ví điểm sinh viên).
            </Alert>
          </>
        }
      >
        <InfoRow label="Biển số" value={<span className="font-mono">{formatPlate(result.plateNumber)}</span>} />
        <InfoRow label="Sinh viên" value={`${result.fullName} (${result.studentCode})`} />
        <InfoRow
          label="Tiền mặt đã thu"
          value={
            <span className="text-emerald-700">
              {formatCash(result.amount)} {result.overnight ? '(Phí qua đêm)' : ''}
            </span>
          }
        />
        <InfoRow label="Giờ thu" value={formatDateTime(result.collectedAt)} />
        <InfoRow label="Mã phiếu thu" value={`#${result.collectionId}`} />
      </SuccessPanel>
    );
  }

  if (vietQrOut.data) {
    const result = vietQrOut.data.data;
    return (
      <SuccessPanel
        title="Đã thanh toán VietQR và cho xe ra"
        onNext={nextVehicle}
        notice={
          vietQrOut.data.replayed && <Alert tone="info">Lượt VietQR này đã được xác nhận trước đó.</Alert>
        }
      >
        <InfoRow label="Biển số" value={<span className="font-mono">{formatPlate(result.plateNumber)}</span>} />
        <InfoRow label="Số tiền thanh toán" value={<span className="text-emerald-700">{formatCash(result.fee)}</span>} />
        <InfoRow label="Điểm ví còn lại" value={formatPoints(result.balanceAfter)} />
        <InfoRow label="Giờ ra" value={formatDateTime(result.exitedAt)} />
        <InfoRow label="Mã giao dịch" value={`#${result.transactionId}`} />
      </SuccessPanel>
    );
  }

  const data = preview.data;
  const busy = checkOut.isPending || cashOut.isPending || vietQrOut.isPending;
  const isCurfew = data?.reason === 'CHECKOUT_CURFEW';
  const canConfirm = data?.allowed === true && data.sessionId !== null && pointKey.key !== null;
  const canCash =
    data !== undefined &&
    data.sessionId !== null &&
    !isCurfew &&
    !data.passValidUntil &&
    (data.fee ?? 0) > 0 &&
    cashKey.key !== null;
  const canVietQr =
    data !== undefined &&
    data.sessionId !== null &&
    !isCurfew &&
    data.reason === 'INSUFFICIENT_POINTS' &&
    Boolean(data.vietQrOrder) &&
    vietQrKey.key !== null;

  return (
    <div className="space-y-4">
      <Card>
        <PlateLookupForm
          key={formKey}
          ref={inputRef}
          onLookup={lookup}
          pending={preview.isPending}
          disabled={busy}
          parkedOnly
          onEdit={() => {
            preview.reset();
            clearMutations();
          }}
        />
      </Card>
      {preview.isError && <Alert tone="error">{errorMessage(preview.error)}</Alert>}
      {data && showVietQr && data.vietQrOrder && data.sessionId !== null && vietQrKey.key !== null ? (
        <VietQrPaymentCard
          order={data.vietQrOrder}
          confirming={vietQrOut.isPending}
          error={vietQrOut.isError ? errorMessage(vietQrOut.error) : null}
          onCancel={() => {
            vietQrOut.reset();
            setShowVietQr(false);
          }}
          onConfirm={() => {
            if (data.sessionId !== null && vietQrKey.key) {
              vietQrOut.mutate({ sessionId: data.sessionId, key: vietQrKey.key });
            }
          }}
        />
      ) : (
        data && (
          <Card>
            <div className="space-y-4">
              <VerdictBanner
                allowed={data.allowed}
                allowedText="Cho xe ra"
                reason={data.reason}
                curfewTime={data.curfewTime}
                shortfall={data.balance === null ? null : (data.fee ?? 0) - data.balance}
              />
              {data.fullName !== null && (
                <dl className="text-lg">
                  <InfoRow label="Biển số" value={<span className="font-mono">{formatPlate(data.plateNumber ?? '')}</span>} />
                  <InfoRow label="Sinh viên" value={data.fullName} />
                  <InfoRow label="MSSV" value={data.studentCode} />
                  <InfoRow label="Loại xe" value={typeName(data.vehicleTypeCode)} />
                  <InfoRow label="Giờ vào" value={data.enteredAt ? formatDateTime(data.enteredAt) : '—'} />
                  <InfoRow label="Số điểm hiện có" value={data.balance === null ? '—' : formatPoints(data.balance)} />
                  {data.passValidUntil ? (
                    <InfoRow
                      label="Phí gửi xe"
                      value={<span className="text-xl font-bold text-emerald-700">Miễn phí — vé tháng đến {formatDay(data.passValidUntil)}</span>}
                    />
                  ) : (
                    <InfoRow
                      label="Phí gửi xe"
                      value={
                        data.fee === null ? (
                          '—'
                        ) : (
                          <span className="inline-flex flex-wrap items-center justify-end gap-2">
                            {data.overnight && <Badge tone="amber">🌙 Phí qua đêm</Badge>}
                            <span className="text-xl font-bold text-red-600">{formatPoints(data.fee)}</span>
                          </span>
                        )
                      }
                    />
                  )}
                </dl>
              )}
              {checkOut.isError && <Alert tone="error">{errorMessage(checkOut.error)}</Alert>}
              {cashOut.isError && <Alert tone="error">{errorMessage(cashOut.error)}</Alert>}
              <div className="flex flex-col gap-3 sm:flex-row">
                <Button variant="secondary" size="xl" className="sm:w-1/3" onClick={nextVehicle} disabled={busy}>
                  Hủy
                </Button>
                <Button
                  variant="success"
                  size="xl"
                  className="flex-1"
                  disabled={!canConfirm || busy}
                  loading={checkOut.isPending}
                  onClick={() => {
                    // Bấm lại sau lỗi mạng: GIỮ NGUYÊN key -> backend không trừ điểm hai lần
                    if (data.sessionId !== null && pointKey.key) {
                      checkOut.mutate({ sessionId: data.sessionId, key: pointKey.key });
                    }
                  }}
                >
                  Xác nhận cho xe ra
                </Button>
              </div>
              {(canCash || canVietQr) && (
                <div className="flex flex-col gap-3 border-t border-slate-100 pt-3 sm:flex-row">
                  {canVietQr && (
                    <Button
                      variant="primary"
                      size="lg"
                      className="flex-1"
                      disabled={busy}
                      onClick={() => {
                        vietQrOut.reset();
                        setShowVietQr(true);
                      }}
                    >
                      Thu qua VietQR tại cổng
                    </Button>
                  )}
                  {canCash && (
                    <Button
                      variant="secondary"
                      size="lg"
                      className="flex-1 border-amber-300 bg-amber-50 text-amber-900 hover:bg-amber-100"
                      disabled={busy}
                      loading={cashOut.isPending}
                      onClick={() => {
                        if (data.sessionId !== null && cashKey.key) {
                          cashOut.mutate({ sessionId: data.sessionId, key: cashKey.key });
                        }
                      }}
                    >
                      Thu tiền mặt ({formatCash(data.fee ?? 0)})
                    </Button>
                  )}
                </div>
              )}
            </div>
          </Card>
        )
      )}
    </div>
  );
}
