import type { ApiClient, Idempotent } from './ApiClient';
import { ApiError, isApiErrorBody } from './ApiError';
import { endsSession, notifySessionEnded } from './sessionEvents';

type Method = 'GET' | 'POST' | 'PATCH';
type QueryValue = string | number | undefined;

interface RequestOptions {
  method?: Method;
  body?: unknown;
  query?: Record<string, QueryValue>;
  idempotencyKey?: string;
}

const LOGIN_PATH = '/auth/login';
/** Đặc tả API mục 3: login và register không cần CSRF vì lúc đó chưa có phiên. */
const CSRF_EXEMPT = new Set([LOGIN_PATH, '/students/register']);
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

export function readCookie(name: string): string | null {
  for (const part of document.cookie.split(';')) {
    const [key, ...rest] = part.trim().split('=');
    if (key === name) return decodeURIComponent(rest.join('='));
  }
  return null;
}

/**
 * Gọi backend thật bằng fetch. Base URL là đường dẫn TƯƠNG ĐỐI: giao diện và API cùng origin
 * (Vite proxy khi dev, Nginx khi triển khai), nên cookie phiên SESSION tự đi kèm, không cần CORS.
 */
export function createHttpApiClient(baseUrl = '/api/v1', fetchFn: typeof fetch = (...args) => fetch(...args)): ApiClient {
  function url(path: string, query?: Record<string, QueryValue>): string {
    const params = new URLSearchParams();
    Object.entries(query ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== '') params.set(key, String(value));
    });
    const search = params.toString();
    return `${baseUrl}${path}${search ? `?${search}` : ''}`;
  }

  /** Lấy cookie XSRF-TOKEN: backend gửi kèm cookie này trong response của bất kỳ request nào. */
  async function refreshCsrfToken(): Promise<void> {
    try {
      await fetchFn(url('/me'), { credentials: 'include', headers: { Accept: 'application/json' } });
    } catch {
      // Lỗi mạng sẽ hiện ra ở request chính ngay sau đó
    }
  }

  async function send(path: string, options: RequestOptions, csrfRetried = false): Promise<Response> {
    const method = options.method ?? 'GET';
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey;
    if (method !== 'GET' && !CSRF_EXEMPT.has(path)) {
      if (readCookie(CSRF_COOKIE) === null) await refreshCsrfToken();
      const token = readCookie(CSRF_COOKIE);
      if (token !== null) headers[CSRF_HEADER] = token;
    }

    let response: Response;
    try {
      response = await fetchFn(url(path, options.query), {
        method,
        headers,
        credentials: 'include',
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
      });
    } catch {
      throw ApiError.network();
    }
    if (response.ok) return response;

    const error = await toApiError(response);
    // Token CSRF đổi sau khi đăng nhập: lấy token mới rồi gửi lại MỘT lần. An toàn kể cả với POST
    // vì request bị chặn CSRF chưa hề tới nghiệp vụ.
    if (error.code === 'CSRF_INVALID' && !csrfRetried) {
      await refreshCsrfToken();
      return send(path, options, true);
    }
    if (path !== LOGIN_PATH && endsSession(error.code)) notifySessionEnded(error);
    throw error;
  }

  async function json<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const response = await send(path, options);
    return (await response.json()) as T;
  }

  async function idempotent<T>(path: string, body: unknown, idempotencyKey: string): Promise<Idempotent<T>> {
    const response = await send(path, { method: 'POST', body, idempotencyKey });
    return {
      data: (await response.json()) as T,
      replayed: response.headers.get('Idempotent-Replayed') === 'true',
    };
  }

  return {
    login: (body) => json(LOGIN_PATH, { method: 'POST', body }),
    logout: async () => {
      await send('/auth/logout', { method: 'POST' });
    },
    me: () => json('/me'),
    registerStudent: (body) => json('/students/register', { method: 'POST', body }),
    vehicleTypes: () => json('/vehicle-types'),
    feeRules: () => json('/fee-rules'),
    exchangeRate: () => json('/config/exchange-rate'),

    myVehicles: () => json('/me/vehicles'),
    registerVehicle: (body) => json('/me/vehicles', { method: 'POST', body }),
    myWallet: () => json('/me/wallet'),
    myTransactions: (query) =>
      json('/me/transactions', { query: { type: query.type, limit: query.limit, before: query.before } }),

    suggestPlates: (q, parkedOnly) => json('/guard/plates', { query: { q, parked: String(parkedOnly) } }),
    previewCheckIn: (plate) => json('/guard/check-in/preview', { query: { plate } }),
    checkIn: (plateNumber) => json('/guard/check-in', { method: 'POST', body: { plateNumber } }),
    previewCheckOut: (plate) => json('/guard/check-out/preview', { query: { plate } }),
    checkOut: (sessionId, key) => idempotent('/guard/check-out', { sessionId }, key),

    lookupStudent: (studentCode) => json(`/manager/students/${encodeURIComponent(studentCode.trim())}`),
    deposit: (body, key) => idempotent('/manager/deposits', body, key),
    ownerWallet: () => json('/manager/owner-wallet'),
    withdraw: (points, key) => idempotent('/manager/withdrawals', { points }, key),
    guards: () => json('/manager/guards'),
    createGuard: (body) => json('/manager/guards', { method: 'POST', body }),
    changeGuardStatus: (guardId, status) =>
      json(`/manager/guards/${guardId}/status`, { method: 'PATCH', body: { status } }),
  };
}

async function toApiError(response: Response): Promise<ApiError> {
  const retryAfter = response.headers.get('Retry-After');
  const retryAfterSeconds = retryAfter !== null && /^\d+$/.test(retryAfter) ? Number(retryAfter) : null;
  try {
    const body: unknown = await response.json();
    if (isApiErrorBody(body)) {
      return new ApiError({ ...body, status: body.status ?? response.status }, retryAfterSeconds);
    }
  } catch {
    // Không phải JSON (ví dụ trang lỗi của proxy)
  }
  return ApiError.unexpected(response.status);
}
