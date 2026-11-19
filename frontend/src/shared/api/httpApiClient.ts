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
const CSRF_EXEMPT = new Set([LOGIN_PATH, '/students/register', '/auth/password-reset', '/auth/password-reset/confirm']);
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
    guards: () => json('/manager/guards'),
    createGuard: (body) => json('/manager/guards', { method: 'POST', body }),
    changeGuardStatus: (guardId, status) =>
      json(`/manager/guards/${guardId}/status`, { method: 'PATCH', body: { status } }),

    myProfile: () => json('/me/profile'),
    updateMyProfile: (body) => json('/me/profile', { method: 'PATCH', body }),
    changePassword: async (body) => {
      await send('/me/password', { method: 'POST', body });
    },
    requestPasswordReset: (email) => json('/auth/password-reset', { method: 'POST', body: { email } }),
    confirmPasswordReset: async (body) => {
      await send('/auth/password-reset/confirm', { method: 'POST', body });
    },

    changeVehicleType: (vehicleId, vehicleTypeCode) =>
      json(`/me/vehicles/${vehicleId}`, { method: 'PATCH', body: { vehicleTypeCode } }),
    deactivateVehicle: (vehicleId) => json(`/me/vehicles/${vehicleId}/deactivate`, { method: 'POST' }),

    resetStudentPassword: (studentCode) =>
      json(`/manager/students/${encodeURIComponent(studentCode.trim())}/password-reset`, { method: 'POST' }),
    resetGuardPassword: (guardId) => json(`/manager/guards/${guardId}/password-reset`, { method: 'POST' }),
    updateGuard: (guardId, body) => json(`/manager/guards/${guardId}`, { method: 'PATCH', body }),
    auditLogs: (query) =>
      json('/manager/audit-logs', {
        query: { before: query.before, limit: query.limit, targetUserId: query.targetUserId },
      }),

    parkedVehicles: (fragment) => json('/guard/parked-vehicles', { query: { q: fragment } }),
    myGateActivity: () => json('/guard/my-activity'),
    currentShift: () => json('/guard/shifts/current'),
    startShift: (acknowledgedHandoverIds, openingCash) =>
      json('/guard/shifts/start', { method: 'POST', body: { acknowledgedHandoverIds, openingCash } }),
    endShift: (note, closingCash) => json('/guard/shifts/end', { method: 'POST', body: { note, closingCash } }),
    myShiftHistory: () => json('/guard/shifts/history'),
    guardTimesheet: (from, to, guardId) => json('/manager/guard-shifts', { query: { from, to, guardId } }),

    ownerOverview: () => json('/owner/overview'),
    ownerRevenue: (from, to) => json('/owner/revenue', { query: { from, to } }),
    myWithdrawalRequests: () => json('/owner/withdrawal-requests'),
    createWithdrawalRequest: (points) => json('/owner/withdrawal-requests', { method: 'POST', body: { points } }),
    cancelWithdrawalRequest: (id) => json(`/owner/withdrawal-requests/${id}/cancel`, { method: 'POST' }),
    confirmWithdrawalRequest: (id) => json(`/owner/withdrawal-requests/${id}/confirm`, { method: 'POST' }),
    complainWithdrawalRequest: (id, note) =>
      json(`/owner/withdrawal-requests/${id}/complain`, { method: 'POST', body: { note } }),

    withdrawalQueue: (status) => json('/manager/withdrawal-requests', { query: { status } }),
    payWithdrawalRequest: (id) => json(`/manager/withdrawal-requests/${id}/pay`, { method: 'POST' }),
    rejectWithdrawalRequest: (id, note) =>
      json(`/manager/withdrawal-requests/${id}/reject`, { method: 'POST', body: { note } }),
    resolveWithdrawalRequest: (id, note) =>
      json(`/manager/withdrawal-requests/${id}/resolve`, { method: 'POST', body: { note } }),
    adjustPoints: (body, key) => idempotent('/manager/adjustments', body, key),
    reportIncident: (body) => json('/guard/incidents', { method: 'POST', body }),
    myIncidents: () => json('/guard/incidents'),
    incidentPhotoUrl: (incidentId) => url(`/guard/incidents/${incidentId}/photo`),
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
