import jsQR from 'jsqr';
import { useEffect, useRef, useState } from 'react';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { plateFromQr } from '../../shared/utils/plateQr';

const SCAN_INTERVAL_MS = 200;
/** Thu nhỏ khung hình trước khi giải mã cho nhẹ máy tính bảng; mã QR trên điện thoại vẫn đủ rõ. */
const MAX_SCAN_WIDTH = 640;

function cameraErrorMessage(error: unknown): string {
  const name = error instanceof DOMException ? error.name : '';
  if (name === 'NotAllowedError') return 'Chưa cho phép dùng camera. Hãy bấm "Cho phép" khi trình duyệt hỏi, hoặc bật lại trong cài đặt trang.';
  if (name === 'NotFoundError' || name === 'OverconstrainedError') return 'Không tìm thấy camera trên thiết bị này.';
  if (name === 'NotReadableError') return 'Camera đang được ứng dụng khác sử dụng.';
  return 'Không mở được camera. Vui lòng gõ biển số.';
}

/**
 * Quét mã QR trên điện thoại sinh viên bằng camera sau của máy tính bảng. Đọc được mã gửi xe thì tắt camera
 * và trả biển số; bảo vệ vẫn đối chiếu biển số hiện trên màn hình với biển số thật của xe.
 */
export function QrScannerDialog({ onResult, onClose }: { onResult: (plate: string) => void; onClose: () => void }) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const onResultRef = useRef(onResult);
  onResultRef.current = onResult;
  const [error, setError] = useState<string | null>(null);
  const [notOurCode, setNotOurCode] = useState(false);

  useEffect(() => {
    // Trình duyệt chỉ cho dùng camera trong "secure context": https hoặc localhost
    if (!navigator.mediaDevices?.getUserMedia) {
      setError('Camera chỉ dùng được khi mở trang bằng https (hoặc localhost). Vui lòng gõ biển số.');
      return undefined;
    }
    const canvas = document.createElement('canvas');
    const context = canvas.getContext('2d', { willReadFrequently: true });
    let stream: MediaStream | null = null;
    let timer: number | undefined;
    let stopped = false;

    function stop() {
      stopped = true;
      window.clearInterval(timer);
      stream?.getTracks().forEach((track) => track.stop());
    }

    function scanFrame() {
      const video = videoRef.current;
      if (!video || !context || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || video.videoWidth === 0) return;
      const scale = Math.min(1, MAX_SCAN_WIDTH / video.videoWidth);
      canvas.width = Math.round(video.videoWidth * scale);
      canvas.height = Math.round(video.videoHeight * scale);
      context.drawImage(video, 0, 0, canvas.width, canvas.height);
      const image = context.getImageData(0, 0, canvas.width, canvas.height);
      const code = jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' });
      if (!code) return;
      const plate = plateFromQr(code.data);
      if (plate) {
        stop();
        onResultRef.current(plate);
      } else {
        setNotOurCode(true);
      }
    }

    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false })
      .then((media) => {
        if (stopped) {
          media.getTracks().forEach((track) => track.stop());
          return;
        }
        stream = media;
        const video = videoRef.current;
        if (video) {
          video.srcObject = media;
          void video.play().catch(() => undefined);
        }
        timer = window.setInterval(scanFrame, SCAN_INTERVAL_MS);
      })
      .catch((cause: unknown) => {
        if (!stopped) setError(cameraErrorMessage(cause));
      });

    return stop;
  }, []);

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
