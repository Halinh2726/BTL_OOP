import { useMutation } from '@tanstack/react-query';
import { api, errorMessage } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import { formatDateTime, formatPoints } from '../../shared/utils/format';
import { PlateLookupForm } from './PlateLookupForm';
import { SuccessPanel } from './SuccessPanel';
import { usePlateFocus } from './usePlateFocus';
import { VerdictBanner } from './VerdictBanner';

/** BV01 — xe vào: tra cứu -> xem kết luận -> xác nhận cho vào. Chỉ kiểm tra điểm, KHÔNG trừ điểm. */
export function CheckInPanel() {
  const { inputRef, formKey, resetForm } = usePlateFocus();
  const typeName = useVehicleTypeName();
  const preview = useMutation({ mutationFn: api.previewCheckIn });
  const checkIn = useMutation({ mutationFn: api.checkIn });

  function lookup(plate: string) {
    checkIn.reset();
    preview.mutate(plate);
  }

  function nextVehicle() {
    preview.reset();
    checkIn.reset();
    resetForm();
  }

  if (checkIn.data) {
    return (
      <SuccessPanel title="Đã cho xe vào" onNext={nextVehicle}>
        <InfoRow label="Biển số" value={<span className="font-mono">{checkIn.data.plateNumber}</span>} />
        <InfoRow label="Mã lượt gửi" value={`#${checkIn.data.sessionId}`} />
        <InfoRow label="Giờ vào" value={formatDateTime(checkIn.data.enteredAt)} />
      </SuccessPanel>
    );
  }

  const data = preview.data;
  return (
    <div className="space-y-4">
      <Card>
        <PlateLookupForm key={formKey} ref={inputRef} onLookup={lookup} pending={preview.isPending} disabled={checkIn.isPending} />
      </Card>
      {preview.isError && <Alert tone="error">{errorMessage(preview.error)}</Alert>}
      {data && (
        <Card>
          <div className="space-y-4">
            <VerdictBanner allowed={data.allowed} allowedText="Cho xe vào" reason={data.reason} />
            {data.fullName !== null && (
              <dl className="text-lg">
                <InfoRow label="Biển số" value={<span className="font-mono">{data.plateNumber}</span>} />
                <InfoRow label="Sinh viên" value={data.fullName} />
                <InfoRow label="MSSV" value={data.studentCode} />
                <InfoRow label="Loại xe" value={typeName(data.vehicleTypeCode)} />
                <InfoRow label="Số điểm hiện có" value={data.balance === null ? '—' : formatPoints(data.balance)} />
                <InfoRow label="Mức phí tối thiểu" value={formatPoints(data.minFee)} />
              </dl>
            )}
            {checkIn.isError && <Alert tone="error">{errorMessage(checkIn.error)}</Alert>}
            <div className="flex flex-col gap-3 sm:flex-row">
              <Button variant="secondary" size="xl" className="sm:w-1/3" onClick={nextVehicle} disabled={checkIn.isPending}>
                Hủy
              </Button>
              <Button
                variant="success"
                size="xl"
                className="flex-1"
                disabled={!data.allowed || data.plateNumber === null}
                loading={checkIn.isPending}
                onClick={() => data.plateNumber && checkIn.mutate(data.plateNumber)}
              >
                Xác nhận cho xe vào
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
