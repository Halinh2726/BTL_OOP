import { describe, expect, it } from 'vitest';
import { guestLookup, guestTokenFromQr } from './guestTicket';
import { plateFromQr } from './plateQr';

const token = '0123456789abcdef0123456789abcdef';

describe('vé xe khách', () => {
  it('nhận vé từ QR nội bộ và link vé công khai, giữ QR sinh viên ở luồng cũ', () => {
    expect(guestTokenFromQr(`PTIT-GUEST:${token}`)).toBe(token);
    expect(guestTokenFromQr(`https://parking.example/ve/${token}`)).toBe(token);
    expect(guestTokenFromQr('GUIXE:29B112345')).toBeNull();
    expect(plateFromQr('GUIXE:29B112345')).toBe('29B112345');
    expect(guestTokenFromQr('PTIT-GUEST:too-short')).toBeNull();
    expect(guestTokenFromQr(`https://parking.example/ve/${token}/other`)).toBeNull();
  });

  it('phân biệt mã vé, biển số và token trong tra cứu', () => {
    expect(guestLookup('k-261120-0001')).toEqual({ ticketCode: 'K-261120-0001' });
    expect(guestLookup('29b1-123.45')).toEqual({ plateNumber: '29B1-123.45' });
    expect(guestLookup(`PTIT-GUEST:${token}`)).toEqual({ ticketToken: token });
  });
});
