import jsQR from 'jsqr';
import { useEffect, useRef, useState, type RefObject } from 'react';

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
 * Mở camera sau và đọc mã QR liên tục (mỗi 200 ms). Dùng chung cho hộp thoại quét một lần và chế độ quét liên tục.
 * {@code paused}: vẫn hiện hình nhưng tạm không giải mã (đang xử lý xe trước) — đỡ tốn pin, không đọc trùng.
 * Camera tắt khi component bị gỡ.
 */
export function useQrCamera(videoRef: RefObject<HTMLVideoElement | null>, onCode: (text: string) => void, paused = false) {
  const [error, setError] = useState<string | null>(null);
  const onCodeRef = useRef(onCode);
  onCodeRef.current = onCode;
  const pausedRef = useRef(paused);
  pausedRef.current = paused;

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

    function scanFrame() {
      const video = videoRef.current;
      if (pausedRef.current || !video || !context || video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA || video.videoWidth === 0) {
        return;
      }
      const scale = Math.min(1, MAX_SCAN_WIDTH / video.videoWidth);
      canvas.width = Math.round(video.videoWidth * scale);
      canvas.height = Math.round(video.videoHeight * scale);
      context.drawImage(video, 0, 0, canvas.width, canvas.height);
      const image = context.getImageData(0, 0, canvas.width, canvas.height);
      const code = jsQR(image.data, image.width, image.height, { inversionAttempts: 'dontInvert' });
      if (code) onCodeRef.current(code.data);
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

    return () => {
      stopped = true;
      window.clearInterval(timer);
      stream?.getTracks().forEach((track) => track.stop());
    };
  }, [videoRef]);

  return { error };
}
