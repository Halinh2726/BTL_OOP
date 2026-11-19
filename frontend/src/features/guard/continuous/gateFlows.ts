import { api } from '../../../shared/api';
import { formatDateTime, formatDay, formatPlate, formatPoints } from '../../../shared/utils/format';
import type { Outcome, Verdict } from './scanMachine';

/**
 * Một "luồng cổng" cho chế độ quét liên tục (mẫu Strategy): khung quét, đếm ngược, âm thanh là CHUNG;
 * chỉ khác cách tra cứu và cách xác nhận. Xe vào và xe ra là hai cài đặt của cùng interface.
 */
export interface GateFlow {
  readonly title: string;
  /** "Tự cho xe vào sau 2 giây" */
  readonly countdownLabel: string;
  /** Gợi ý biển số khi gõ tay: xe ra chỉ gợi ý xe đang trong bãi. */
  readonly parkedOnly: boolean;
  preview(plate: string): Promise<Verdict>;
  /** {@code key}: Idempotency-Key của lượt này — thử lại phải dùng đúng key cũ. */
  confirm(verdict: Verdict, key: string): Promise<Outcome>;
}

/** BV01 — xe vào: chỉ kiểm tra đủ mức phí thấp nhất, KHÔNG trừ điểm (R04). */
export const checkInFlow: GateFlow = {
  title: 'Xe vào',
  countdownLabel: 'Tự cho xe vào sau',
  parkedOnly: false,
  async preview(plate) {
    const result = await api.previewCheckIn(plate);
    return {
      plate,
      plateDisplay: formatPlate(result.plateNumber ?? plate),
      allowed: result.allowed,
      reason: result.reason,
      fullName: result.fullName,
      studentCode: result.studentCode,
      vehicleTypeCode: result.vehicleTypeCode,
      detail: result.balance === null ? null : `Số điểm: ${formatPoints(result.balance)}`,
      shortfall: result.balance === null ? null : result.minFee - result.balance,
    };
  },
  async confirm(verdict) {
    const result = await api.checkIn(verdict.plate);
    return {
      title: 'Đã cho xe vào',
      lines: [
        ['Biển số', formatPlate(result.plateNumber)],
        ['Giờ vào', formatDateTime(result.enteredAt)],
      ],
    };
  },
};

/** Lượt gửi đang mở của từng biển số, nhớ lại từ bước tra cứu để bước xác nhận biết thanh toán lượt nào. */
const sessionByPlate = new Map<string, number>();

/** BV02 «include» HT01 — xe ra: backend tự trừ phí sinh viên, cộng cho chủ bãi xe; có Idempotency-Key. */
export const checkOutFlow: GateFlow = {
  title: 'Xe ra',
  countdownLabel: 'Tự cho xe ra và trừ điểm sau',
  parkedOnly: true,
  async preview(plate) {
    const result = await api.previewCheckOut(plate);
    if (result.sessionId !== null) sessionByPlate.set(plate, result.sessionId);
    return {
      plate,
      plateDisplay: formatPlate(result.plateNumber ?? plate),
      allowed: result.allowed && result.sessionId !== null,
      reason: result.reason,
      fullName: result.fullName,
      studentCode: result.studentCode,
      vehicleTypeCode: result.vehicleTypeCode,
      detail: result.passValidUntil ? `Vé tháng đến ${formatDay(result.passValidUntil)} — miễn phí`
        : result.fee === null ? null : `Phí: ${formatPoints(result.fee)}`,
      shortfall: result.fee === null || result.balance === null ? null : result.fee - result.balance,
    };
  },
  async confirm(verdict, key) {
    const sessionId = sessionByPlate.get(verdict.plate);
    if (sessionId === undefined) throw new Error('Không tìm thấy lượt gửi của xe này. Hãy quét lại.');
    const { data, replayed } = await api.checkOut(sessionId, key);
    sessionByPlate.delete(verdict.plate);
    return {
      title: 'Đã cho xe ra',
      lines: [
        data.transactionId === null ? ['Vé tháng', `Miễn phí — đến ${formatDay(data.passValidUntil ?? '')}`] : ['Phí đã trừ', formatPoints(data.fee)],
        ['Điểm còn lại', formatPoints(data.balanceAfter)],
        ['Giờ ra', formatDateTime(data.exitedAt)],
      ],
      notice: replayed ? 'Lượt này đã được thanh toán trước đó — không trừ điểm lần hai.' : undefined,
    };
  },
};
