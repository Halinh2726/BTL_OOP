import { describe, expect, it } from 'vitest';
import { buildVietQrPayload } from './vietQr';

describe('vietQr', () => {
  it('sinh mã Napas247 EMVCo đúng định dạng', () => {
    const payload = buildVietQrPayload({
      bankId: 'MB',
      accountNo: '0987654321',
      amount: 50000,
      message: 'NAP B21DCCN001',
    });

    // Bắt đầu bằng 000201010212 (phiên bản 01, động)
    expect(payload.startsWith('000201010212')).toBe(true);
    // Chứa GUID Napas
    expect(payload).toContain('A000000727');
    // Chứa số tài khoản
    expect(payload).toContain('0987654321');
    // Chứa tiền tệ VND (704)
    expect(payload).toContain('5303704');
    // Chứa số tiền 50000
    expect(payload).toContain('540550000');
    // Chứa nội dung NAP B21DCCN001
    expect(payload).toContain('NAP B21DCCN001');
    // Kết thúc bằng 4 ký tự checksum CRC16
    expect(payload.length).toBeGreaterThan(60);
  });

  it('sinh mã tĩnh khi không truyền số tiền', () => {
    const payload = buildVietQrPayload({
      bankId: 'MB',
      accountNo: '0987654321',
      message: 'NAP B21DCCN001',
    });

    expect(payload.startsWith('000201010211')).toBe(true); // 11 = Static
    expect(payload).not.toContain('5405');
  });
});

