/**
 * Máy trạng thái của chế độ QUÉT LIÊN TỤC — hàm thuần (không gọi API, không đụng camera) nên test được dễ dàng.
 *
 *   scanning ──quét được mã──▶ checking ──được qua──▶ countdown ──hết 2 giây / "Xác nhận ngay"──▶ confirming ──▶ done
 *       ▲                         │                      │ "Dừng"                                   │ lỗi
 *       │                         └──bị từ chối──▶ denied └──────▶ stopped                          ▼
 *       └──────────────────────── NEXT (tự động sau vài giây hoặc bảo vệ chạm) ◀──────────────── failed ──RETRY──▶ confirming
 *
 * Đếm ngược thay cho nút "Xác nhận": bảo vệ không phải chạm vào màn hình, nhưng vẫn có 2 giây nhìn tên / biển số
 * và chạm "Dừng" nếu không khớp (giữ tinh thần R07 — đối chiếu trước khi cho qua).
 */

export const COUNTDOWN_MS = 2000;

/** Kết quả tra cứu của một xe, chung cho xe vào và xe ra. */
export interface Verdict {
  plate: string;
  plateDisplay: string;
  allowed: boolean;
  reason: string | null;
  fullName: string | null;
  studentCode: string | null;
  vehicleTypeCode: string | null;
  /** Dòng thông tin thêm: số điểm (xe vào), phí (xe ra). */
  detail: string | null;
  /** Số điểm còn thiếu khi không đủ điểm. */
  shortfall: number | null;
}

/** Kết quả sau khi xác nhận thành công. */
export interface Outcome {
  title: string;
  lines: Array<[label: string, value: string]>;
  /** Cảnh báo phụ, ví dụ lượt này đã thanh toán từ trước. */
  notice?: string;
}

export type ScanState =
  | { kind: 'scanning' }
  | { kind: 'checking'; plate: string }
  | { kind: 'countdown'; verdict: Verdict; key: string; remainingMs: number }
  | { kind: 'confirming'; verdict: Verdict; key: string }
  | { kind: 'done'; verdict: Verdict; outcome: Outcome }
  | { kind: 'denied'; verdict: Verdict }
  | { kind: 'stopped'; verdict: Verdict }
  | { kind: 'failed'; plate: string; message: string; verdict?: Verdict; key?: string };

export type ScanEvent =
  | { type: 'DETECTED'; plate: string }
  | { type: 'PREVIEWED'; verdict: Verdict; key: string }
  | { type: 'TICK'; elapsedMs: number }
  | { type: 'CONFIRM_NOW' }
  | { type: 'STOP' }
  | { type: 'CONFIRMED'; outcome: Outcome }
  | { type: 'FAILED'; message: string }
  | { type: 'RETRY' }
  | { type: 'NEXT' };

export const INITIAL: ScanState = { kind: 'scanning' };

/** Chuyển trạng thái. Sự kiện không hợp lệ ở trạng thái hiện tại thì bị bỏ qua (trả lại đúng state cũ). */
export function reduce(state: ScanState, event: ScanEvent): ScanState {
  switch (event.type) {
    case 'DETECTED':
      return state.kind === 'scanning' ? { kind: 'checking', plate: event.plate } : state;
    case 'PREVIEWED':
      if (state.kind !== 'checking') return state;
      return event.verdict.allowed
        ? { kind: 'countdown', verdict: event.verdict, key: event.key, remainingMs: COUNTDOWN_MS }
        : { kind: 'denied', verdict: event.verdict };
    case 'TICK': {
      if (state.kind !== 'countdown') return state;
      const remainingMs = state.remainingMs - event.elapsedMs;
      return remainingMs <= 0
        ? { kind: 'confirming', verdict: state.verdict, key: state.key }
        : { ...state, remainingMs };
    }
    case 'CONFIRM_NOW':
      return state.kind === 'countdown' ? { kind: 'confirming', verdict: state.verdict, key: state.key } : state;
    case 'STOP':
      return state.kind === 'countdown' ? { kind: 'stopped', verdict: state.verdict } : state;
    case 'CONFIRMED':
      return state.kind === 'confirming' ? { kind: 'done', verdict: state.verdict, outcome: event.outcome } : state;
    case 'FAILED':
      if (state.kind === 'checking') return { kind: 'failed', plate: state.plate, message: event.message };
      if (state.kind === 'confirming') {
        // Giữ verdict + key: thử lại dùng ĐÚNG Idempotency-Key cũ -> không bao giờ trừ điểm hai lần
        return { kind: 'failed', plate: state.verdict.plate, message: event.message, verdict: state.verdict, key: state.key };
      }
      return state;
    case 'RETRY':
      return state.kind === 'failed' && state.verdict && state.key
        ? { kind: 'confirming', verdict: state.verdict, key: state.key }
        : state;
    case 'NEXT':
      return ['done', 'denied', 'stopped', 'failed'].includes(state.kind) ? INITIAL : state;
  }
}

/** Sau bao lâu thì tự quay lại quét xe kế tiếp (null = chờ bảo vệ chọn). */
export function autoNextDelay(state: ScanState): number | null {
  switch (state.kind) {
    case 'done':
      return 2500;
    case 'denied':
    case 'stopped':
      return 4000;
    case 'failed':
      return state.key ? null : 4000;         // lỗi lúc xác nhận: để bảo vệ chọn "Thử lại" hay "Bỏ qua"
    default:
      return null;
  }
}

/**
 * Chống xử lý lặp: điện thoại sinh viên còn nằm trước camera thì camera đọc mã đó liên tục —
 * một biển số vừa xử lý xong được bỏ qua trong {@link COOLDOWN_MS}.
 */
export class RecentPlates {
  static readonly COOLDOWN_MS = 10_000;

  private readonly handledAt = new Map<string, number>();

  mark(plate: string, now: number): void {
    this.handledAt.set(plate, now);
  }

  isCoolingDown(plate: string, now: number): boolean {
    const at = this.handledAt.get(plate);
    return at !== undefined && now - at < RecentPlates.COOLDOWN_MS;
  }
}
