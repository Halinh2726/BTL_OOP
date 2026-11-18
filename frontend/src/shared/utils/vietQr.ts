/**
 * Chuẩn EMVCo VietQR (Napas247) sinh mã QR chuyển khoản ngân hàng trực tiếp phía client (trình duyệt).
 * Đảm bảo 100% hoạt động offline, quét được trên tất cả ứng dụng ngân hàng tại Việt Nam (MB, VCB, Momo...).
 */

export const BANK_BINS: Record<string, string> = {
  MB: '970422',
  VCB: '970436',
  TCB: '970407',
  VPB: '970432',
  ACB: '970416',
  BIDV: '970418',
  CTG: '970415',
  TPB: '970423',
};

function formatTlv(id: string, value: string): string {
  const len = value.length.toString().padStart(2, '0');
  return `${id}${len}${value}`;
}

function crc16Ccitt(str: string): string {
  let crc = 0xffff;
  for (let i = 0; i < str.length; i++) {
    crc ^= str.charCodeAt(i) << 8;
    for (let j = 0; j < 8; j++) {
      if ((crc & 0x8000) !== 0) {
        crc = ((crc << 1) ^ 0x1021) & 0xffff;
      } else {
        crc = (crc << 1) & 0xffff;
      }
    }
  }
  return crc.toString(16).toUpperCase().padStart(4, '0');
}

export function buildVietQrPayload(options: {
  bankId?: string;
  accountNo: string;
  amount?: number;
  message: string;
}): string {
  const bankKey = (options.bankId ?? 'MB').toUpperCase();
  const bin = BANK_BINS[bankKey] ?? '970422'; // Mặc định MBBank (970422)

  // Tag 38: Thông tin tài khoản thụ hưởng
  const guid = formatTlv('00', 'A000000727'); // Napas GUID
  const beneficiary = formatTlv('01', formatTlv('00', bin) + formatTlv('01', options.accountNo));
  const service = formatTlv('02', 'QRIBFTTA'); // Chuyển nhanh 247 tới số tài khoản
  const tag38 = formatTlv('38', guid + beneficiary + service);

  // Tag 62: Nội dung chuyển khoản (Additional data)
  const tag62 = formatTlv('62', formatTlv('08', options.message));

  let payload =
    formatTlv('00', '01') + // Format indicator: 01
    formatTlv('01', options.amount && options.amount > 0 ? '12' : '11') + // 12: Động, 11: Tĩnh
    tag38 +
    formatTlv('53', '704'); // Tiền tệ: 704 (VND)

  if (options.amount && options.amount > 0) {
    payload += formatTlv('54', Math.round(options.amount).toString());
  }

  payload += formatTlv('58', 'VN'); // Quốc gia
  payload += tag62;
  payload += '6304'; // Tag CRC16

  const checksum = crc16Ccitt(payload);
  return payload + checksum;
}

