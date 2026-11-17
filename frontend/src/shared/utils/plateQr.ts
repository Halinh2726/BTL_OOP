import { normalizePlate } from './format';

/** Tiền tố để máy quét chỉ nhận mã QR của hệ thống, không nhầm với mã QR khác (chuyển khoản, website...). */
const PREFIX = 'GUIXE:';
const PLATE = /^[A-Z0-9]{5,20}$/;

/**
 * Nội dung mã QR của một xe: chỉ có biển số hoặc kèm timestamp làm mới động.
 * Khi có timestamp, mã tương tự vé xe bus / thẻ tàu điện số (tự làm mới định kỳ để chống chụp màn hình).
 */
export function plateQrPayload(plateNumber: string, timestamp?: number): string {
  return timestamp ? `${PREFIX}${plateNumber}:${timestamp}` : `${PREFIX}${plateNumber}`;
}

/** Đọc biển số từ nội dung mã QR quét được; hỗ trợ cả QR tĩnh (GUIXE:PLATE) và QR động (GUIXE:PLATE:TS). */
export function plateFromQr(text: string): string | null {
  const raw = text.trim();
  if (!raw.toUpperCase().startsWith(PREFIX)) return null;
  const body = raw.slice(PREFIX.length);
  const colonIndex = body.indexOf(':');
  const platePart = colonIndex >= 0 ? body.slice(0, colonIndex) : body;
  const plate = normalizePlate(platePart);
  return PLATE.test(plate) ? plate : null;
}
