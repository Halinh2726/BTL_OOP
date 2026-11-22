import { describe, expect, it } from 'vitest';
import { buildVietQrImageUrl, buildVietQrPayload, getBankShortName, normalizeBankAccountName } from './vietQr';

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

  it('tra cứu tên ngân hàng và chuẩn hóa tên chủ tài khoản không dấu', () => {
    expect(getBankShortName('VCB')).toBe('Vietcombank');
    expect(getBankShortName('mb')).toBe('MB Bank');
    expect(getBankShortName('UNKNOWN')).toBe('UNKNOWN');
    expect(normalizeBankAccountName('Nguyễn Văn Độ - PTIT')).toBe('NGUYEN VAN DO PTIT');
  });

  it('sinh URL ảnh VietQR đúng định dạng', () => {
    const url = buildVietQrImageUrl({
      bankId: 'vcb',
      accountNo: '0123-456 789',
      accountName: 'NGUYEN VAN A',
      template: 'compact2',
      amount: 20000,
      message: 'NAP B21DCCN001',
    });
    expect(url).toContain('/VCB-0123456789-compact2.png?amount=20000');
    expect(url).toContain('addInfo=NAP%20B21DCCN001');
  });
});

