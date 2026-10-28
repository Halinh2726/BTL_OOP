import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { Spinner } from '../../shared/components/Spinner';
import type { Vehicle } from '../../shared/types/api';
import { plateQrPayload } from '../../shared/utils/plateQr';

/** Mã QR của xe để đưa bảo vệ quét ở cổng thay cho việc gõ biển số. */
export function VehicleQrDialog({ vehicle, onClose }: { vehicle: Vehicle; onClose: () => void }) {
  const [image, setImage] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    QRCode.toDataURL(plateQrPayload(vehicle.plateNumber), { width: 320, margin: 2, errorCorrectionLevel: 'M' })
      .then((url) => active && setImage(url))
      .catch(() => active && setFailed(true));
    return () => {
      active = false;
    };
  }, [vehicle.plateNumber]);

  return (
    <Dialog
      title="Mã QR gửi xe"
      onClose={onClose}
      footer={
        <Button variant="secondary" onClick={onClose}>
          Đóng
        </Button>
      }
    >
      <div className="flex flex-col items-center gap-3">
        <div className="flex h-72 w-72 items-center justify-center rounded-xl bg-white ring-1 ring-slate-200">
          {image ? (
            <img src={image} alt={`Mã QR của xe ${vehicle.plateNumber}`} className="h-full w-full" />
          ) : failed ? (
            <p className="px-4 text-center text-red-600">Không tạo được mã QR. Vui lòng thử lại.</p>
          ) : (
            <Spinner className="h-8 w-8" />
          )}
        </div>
        <p className="font-mono text-2xl font-bold tracking-wider">{vehicle.plateNumber}</p>
        <p className="text-center text-sm text-slate-500">
          Đưa mã này cho bảo vệ quét khi xe vào và ra. Tăng độ sáng màn hình nếu quét không được.
        </p>
      </div>
    </Dialog>
  );
}
