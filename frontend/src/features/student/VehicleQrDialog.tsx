import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { Spinner } from '../../shared/components/Spinner';
import type { Vehicle } from '../../shared/types/api';
import { plateQrPayload } from '../../shared/utils/plateQr';

const REFRESH_INTERVAL_SECONDS = 30;

/** Mã QR vé gửi xe điện tử (E-Pass) hiển thị trên app sinh viên, tự động làm mới như vé xe bus/tàu điện. */
export function VehicleQrDialog({ vehicle, onClose }: { vehicle: Vehicle; onClose: () => void }) {
  const [image, setImage] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [timestamp, setTimestamp] = useState(() => Date.now());
  const [countdown, setCountdown] = useState(REFRESH_INTERVAL_SECONDS);

  // Bộ đếm lùi làm mới mã định kỳ chống gian lận ảnh chụp màn hình
  useEffect(() => {
    const timer = window.setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          setTimestamp(Date.now());
          return REFRESH_INTERVAL_SECONDS;
        }
        return prev - 1;
      });
    }, 1000);
    return () => window.clearInterval(timer);
  }, []);

  // Sinh ảnh QR mỗi khi timestamp hoặc biển số thay đổi
  useEffect(() => {
    let active = true;
    QRCode.toDataURL(plateQrPayload(vehicle.plateNumber, timestamp), {
      width: 320,
      margin: 2,
      errorCorrectionLevel: 'M',
    })
      .then((url) => active && setImage(url))
      .catch(() => active && setFailed(true));
    return () => {
      active = false;
    };
  }, [vehicle.plateNumber, timestamp]);

  function refreshNow() {
    setTimestamp(Date.now());
    setCountdown(REFRESH_INTERVAL_SECONDS);
  }

  return (
    <Dialog
      title="Vé xe điện tử 1 chạm"
      onClose={onClose}
      footer={
        <div className="flex w-full gap-2">
          <Button variant="secondary" className="flex-1" onClick={refreshNow}>
            Làm mới mã
          </Button>
          <Button variant="primary" className="flex-1" onClick={onClose}>
            Đóng
          </Button>
        </div>
      }
    >
      <div className="flex flex-col items-center gap-3">
        {/* Huy hiệu trạng thái vé xe bus */}
        <div className="flex items-center gap-2 rounded-full bg-emerald-50 px-3 py-1 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-200">
          <span className="relative flex h-2 w-2">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
            <span className="relative inline-flex h-2 w-2 rounded-full bg-emerald-500" />
          </span>
          Vé điện tử sẵn sàng quét · Tự làm mới sau {countdown}s
        </div>

        {/* Thanh đếm lùi trực quan */}
        <div className="h-1.5 w-64 overflow-hidden rounded-full bg-slate-100">
          <div
            className="h-full bg-emerald-500 transition-all duration-1000 ease-linear"
            style={{ width: `${(countdown / REFRESH_INTERVAL_SECONDS) * 100}%` }}
          />
        </div>

        <div className="flex h-72 w-72 items-center justify-center rounded-2xl bg-white p-2 shadow-inner ring-2 ring-slate-200">
          {image ? (
            <img src={image} alt={`Mã QR của xe ${vehicle.plateNumber}`} className="h-full w-full rounded-xl" />
          ) : failed ? (
            <p className="px-4 text-center text-red-600">Không tạo được mã QR. Vui lòng thử lại.</p>
          ) : (
            <Spinner className="h-8 w-8" />
          )}
        </div>

        <p className="font-mono text-3xl font-extrabold tracking-widest text-slate-800">{vehicle.plateNumber}</p>
        <p className="text-center text-sm text-slate-500">
          Giơ mã này trước máy quét tại cổng bãi xe. Hệ thống sẽ tự động xác nhận và trừ phí trong 1 chạm.
        </p>
      </div>
    </Dialog>
  );
}
