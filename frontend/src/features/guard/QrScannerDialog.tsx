import { useRef, useState } from 'react';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { plateFromQr } from '../../shared/utils/plateQr';
import { useQrCamera } from './useQrCamera';

/**
 * Quét MỘT mã QR trên điện thoại sinh viên bằng camera sau của máy tính bảng (chế độ thường). Đọc được mã gửi xe thì
 * trả biển số; bảo vệ vẫn đối chiếu biển số hiện trên màn hình với biển số thật của xe.
 */
export function QrScannerDialog({ onResult, onClose }: { onResult: (plate: string) => void; onClose: () => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [notOurCode, setNotOurCode] = useState(false);
  const done = useRef(false);
  const { error } = useQrCamera(videoRef, (text) => {
    const plate = plateFromQr(text);
    if (!plate) {
      setNotOurCode(true);
      return;
    }
    if (done.current) return;
    done.current = true;
    onResult(plate);
  });

  return (
    <Dialog
      title="Quét mã QR của xe"
      onClose={onClose}
      footer={
        <Button variant="secondary" size="lg" onClick={onClose}>
          Đóng
        </Button>
      }
    >
      {error ? (
        <Alert tone="error">{error}</Alert>
      ) : (
        <>
          <div className="relative overflow-hidden rounded-xl bg-slate-900">
            <video ref={videoRef} muted playsInline autoPlay className="aspect-[4/3] w-full object-cover" />
            <div className="pointer-events-none absolute inset-[15%] rounded-2xl border-4 border-white/80" />
          </div>
          <p className="text-center text-slate-600">Đưa mã QR trên điện thoại của sinh viên vào giữa khung.</p>
          {notOurCode && <Alert tone="warning">Đây không phải mã QR gửi xe của hệ thống.</Alert>}
        </>
      )}
    </Dialog>
  );
}
