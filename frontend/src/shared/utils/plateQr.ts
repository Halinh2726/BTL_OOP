import { normalizePlate } from './format';

/** Tiền tố để máy quét chỉ nhận mã QR của hệ thống, không nhầm với mã QR khác (chuyển khoản, website...). */
const PREFIX = 'GUIXE:';
const PLATE = /^[A-Z0-9]{5,20}$/;

/** Nội dung mã QR của một xe: chỉ có biển số — không chứa thông tin cá nhân hay bí mật nào. */
export function plateQrPayload(plateNumber: string): string {
  return `${PREFIX}${plateNumber}`;
}

/** Đọc biển số từ nội dung mã QR quét được; không phải mã QR biển số -> null. */
export function plateFromQr(text: string): string | null {
  const raw = text.trim();
  if (!raw.toUpperCase().startsWith(PREFIX)) return null;
  const plate = normalizePlate(raw.slice(PREFIX.length));
  return PLATE.test(plate) ? plate : null;
}
