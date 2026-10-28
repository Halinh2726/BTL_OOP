import { describe, expect, it } from 'vitest';
import { parseAmount } from '../../features/manager/amount';
import { newIdempotencyKey } from '../hooks/useIdempotencyKey';
import { formatDateTime, formatNumber, formatSignedPoints, normalizePlate } from './format';
import { plateFromQr, plateQrPayload } from './plateQr';

describe('định dạng hiển thị', () => {
  it('số theo vi-VN', () => {
    expect(formatNumber(50000)).toBe('50.000');
    expect(formatSignedPoints(3000)).toBe('+3.000');
    expect(formatSignedPoints(-3000)).toBe('−3.000');
  });

  it('giờ lấy đúng giờ Việt Nam trong chuỗi, không đổi theo múi giờ máy', () => {
    expect(formatDateTime('2026-10-05T18:30:00+07:00')).toBe('05/10/2026 18:30');
  });

  it('chuẩn hóa biển số giống backend', () => {
    expect(normalizePlate(' 29a-123.45 ')).toBe('29A12345');
  });

  it('đọc số tiền có dấu chấm ngăn cách', () => {
    expect(parseAmount('50.000')).toBe(50000);
    expect(parseAmount('12a')).toBeNull();
    expect(parseAmount('-5')).toBeNull();
  });

  it('mã QR chỉ chứa biển số, có tiền tố để không nhận nhầm mã QR khác', () => {
    expect(plateQrPayload('29A12345')).toBe('GUIXE:29A12345');
    expect(plateFromQr(' guixe:29a-123.45 ')).toBe('29A12345');
    expect(plateFromQr('29A12345')).toBeNull();
    expect(plateFromQr('https://example.com')).toBeNull();
    expect(plateFromQr('GUIXE:12')).toBeNull();
  });

  it('Idempotency-Key là UUID v4', () => {
    expect(newIdempotencyKey()).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });
});
