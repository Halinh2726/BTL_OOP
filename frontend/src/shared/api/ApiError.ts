import type { ApiErrorBody, FieldViolation } from '../types/api';

/** Lỗi từ API. Giao diện xử lý theo {@link code}, hiển thị {@link message} (backend đã viết sẵn tiếng Việt). */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly traceId: string | null;
  readonly fields: FieldViolation[];
  /** Chỉ có với 429 — số giây phải chờ, đọc từ header Retry-After. */
  readonly retryAfterSeconds: number | null;

  constructor(body: ApiErrorBody, retryAfterSeconds: number | null = null) {
    super(body.message);
    this.name = 'ApiError';
    this.code = body.code;
    this.status = body.status;
    this.traceId = body.traceId || null;
    this.fields = body.fields ?? [];
    this.retryAfterSeconds = retryAfterSeconds;
  }

  static network(): ApiError {
    return new ApiError({
      code: 'NETWORK_ERROR',
      message: 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.',
      status: 0,
      traceId: '',
    });
  }

  /** Lỗi không theo cấu trúc chuẩn (ví dụ proxy trả trang HTML khi backend chưa chạy). */
  static unexpected(status: number): ApiError {
    return new ApiError({
      code: 'INTERNAL_ERROR',
      message: 'Có lỗi xảy ra. Vui lòng thử lại.',
      status,
      traceId: '',
    });
  }

  /** Lời nhắn hiển thị cho người dùng, kèm thời gian chờ khi bị giới hạn tần suất. */
  get displayMessage(): string {
    if (this.retryAfterSeconds !== null) {
      return `${this.message} (thử lại sau ${formatWait(this.retryAfterSeconds)})`;
    }
    return this.message;
  }
}

function formatWait(seconds: number): string {
  if (seconds < 60) return `${seconds} giây`;
  const minutes = Math.ceil(seconds / 60);
  return `${minutes} phút`;
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** Lời nhắn của một lỗi bất kỳ, an toàn để hiển thị. */
export function errorMessage(error: unknown): string {
  if (isApiError(error)) return error.displayMessage;
  return 'Có lỗi xảy ra. Vui lòng thử lại.';
}

export function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return typeof candidate.code === 'string' && typeof candidate.message === 'string';
}
