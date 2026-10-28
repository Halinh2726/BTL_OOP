import { useCallback, useState } from 'react';

/** UUID v4. crypto.randomUUID chỉ có trong "secure context" (https, localhost) — máy tính bảng ở cổng
 * mở bằng http://<IP máy chủ> thì không có, nên tự sinh từ crypto.getRandomValues. */
export function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = ((bytes[6] ?? 0) & 0x0f) | 0x40;
  bytes[8] = ((bytes[8] ?? 0) & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Idempotency-Key cho MỘT thao tác (Đặc tả API mục 6): sinh khi mở bước xác nhận, giữ nguyên khi bấm lại
 * hoặc thử lại sau lỗi mạng, chỉ sinh key mới khi bắt đầu thao tác mới.
 */
export function useIdempotencyKey() {
  const [key, setKey] = useState<string | null>(null);
  const start = useCallback(() => setKey(newIdempotencyKey()), []);
  const finish = useCallback(() => setKey(null), []);
  return { key, start, finish };
}
