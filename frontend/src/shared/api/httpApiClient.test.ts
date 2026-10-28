import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './ApiError';
import { createHttpApiClient } from './httpApiClient';
import { onSessionEnded } from './sessionEvents';

interface Call {
  url: string;
  init: RequestInit;
}

function json(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });
}

function error(code: string, status: number, headers: Record<string, string> = {}): Response {
  return json(status, { code, message: `msg ${code}`, status, traceId: 't-1' }, headers);
}

/** fetch giả: trả lần lượt các response đã chuẩn bị, ghi lại mọi lần gọi. */
function fakeFetch(...responses: Array<Response | (() => Response)>) {
  const calls: Call[] = [];
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    calls.push({ url: String(input), init: init ?? {} });
    const next = responses.shift();
    if (!next) throw new Error('Hết response giả');
    return typeof next === 'function' ? next() : next;
  });
  return { fn: fn as unknown as typeof fetch, calls };
}

function header(call: Call | undefined, name: string): string | undefined {
  return (call?.init.headers as Record<string, string> | undefined)?.[name];
}

function clearCookies() {
  document.cookie.split(';').forEach((part) => {
    const name = part.split('=')[0]?.trim();
    if (name) document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
  });
}

describe('httpApiClient', () => {
  beforeEach(() => {
    clearCookies();
    document.cookie = 'XSRF-TOKEN=token-1; path=/';
  });
  afterEach(clearCookies);

  it('gửi cookie phiên và KHÔNG gửi CSRF khi đăng nhập', async () => {
    const me = { user: { id: 1, username: 'sv01', fullName: 'A', role: 'SINH_VIEN' }, permissions: [] };
    const { fn, calls } = fakeFetch(json(200, me));
    const result = await createHttpApiClient('/api/v1', fn).login({ username: 'sv01', password: 'x' });

    expect(result).toEqual(me);
    expect(calls[0]?.url).toBe('/api/v1/auth/login');
    expect(calls[0]?.init.credentials).toBe('include');
    expect(header(calls[0], 'X-XSRF-TOKEN')).toBeUndefined();
  });

  it('gửi header X-XSRF-TOKEN từ cookie với POST/PATCH đã đăng nhập', async () => {
    const { fn, calls } = fakeFetch(json(201, { sessionId: 7, plateNumber: '29A12345', enteredAt: 'x' }));
    await createHttpApiClient('/api/v1', fn).checkIn('29A12345');

    expect(header(calls[0], 'X-XSRF-TOKEN')).toBe('token-1');
    expect(calls[0]?.init.body).toBe(JSON.stringify({ plateNumber: '29A12345' }));
  });

  it('chưa có cookie CSRF thì lấy trước bằng GET /me', async () => {
    clearCookies();
    const { fn, calls } = fakeFetch(
      () => {
        document.cookie = 'XSRF-TOKEN=fresh; path=/';
        return json(200, {});
      },
      json(200, { id: 1, username: 'b', fullName: 'B', phone: '0912345678', status: 'BI_KHOA' }),
    );
    await createHttpApiClient('/api/v1', fn).changeGuardStatus(1, 'BI_KHOA');

    expect(calls[0]?.url).toBe('/api/v1/me');
    expect(calls[1]?.init.method).toBe('PATCH');
    expect(header(calls[1], 'X-XSRF-TOKEN')).toBe('fresh');
  });

  it('CSRF_INVALID -> lấy token mới và gửi lại đúng một lần', async () => {
    const { fn, calls } = fakeFetch(
      error('CSRF_INVALID', 403),
      () => {
        document.cookie = 'XSRF-TOKEN=token-2; path=/';
        return json(200, {});
      },
      json(201, { id: 3, plateNumber: '29A99999', vehicleTypeCode: 'XE_MAY', registeredAt: 'x' }),
    );
    const vehicle = await createHttpApiClient('/api/v1', fn).registerVehicle({ plateNumber: '29A99999', vehicleTypeCode: 'XE_MAY' });

    expect(vehicle.id).toBe(3);
    expect(calls).toHaveLength(3);
    expect(header(calls[2], 'X-XSRF-TOKEN')).toBe('token-2');
  });

  it('gửi Idempotency-Key và nhận biết kết quả trả lại (Idempotent-Replayed)', async () => {
    const body = { transactionId: 9, sessionId: 7, fee: 3000, exitedAt: 'x', balanceAfter: 41000 };
    const { fn, calls } = fakeFetch(json(201, body, { 'Idempotent-Replayed': 'true' }));
    const result = await createHttpApiClient('/api/v1', fn).checkOut(7, 'key-123');

    expect(header(calls[0], 'Idempotency-Key')).toBe('key-123');
    expect(result).toEqual({ data: body, replayed: true });
  });

  it('chuyển lỗi về ApiError, đọc Retry-After khi 429', async () => {
    const { fn } = fakeFetch(error('TOO_MANY_REQUESTS', 429, { 'Retry-After': '42' }));
    const failure = await createHttpApiClient('/api/v1', fn).login({ username: 'a', password: 'b' }).catch((e: unknown) => e);

    expect(failure).toBeInstanceOf(ApiError);
    expect((failure as ApiError).code).toBe('TOO_MANY_REQUESTS');
    expect((failure as ApiError).retryAfterSeconds).toBe(42);
    expect((failure as ApiError).displayMessage).toContain('42 giây');
  });

  it('giữ fields của VALIDATION_ERROR', async () => {
    const fields = [{ field: 'plateNumber', code: 'PATTERN', message: 'Biển số sai' }];
    const { fn } = fakeFetch(json(400, { code: 'VALIDATION_ERROR', message: 'Dữ liệu không hợp lệ.', status: 400, traceId: 't', fields }));
    const failure = (await createHttpApiClient('/api/v1', fn)
      .registerVehicle({ plateNumber: 'x', vehicleTypeCode: 'XE_MAY' })
      .catch((e: unknown) => e)) as ApiError;

    expect(failure.fields).toEqual(fields);
  });

  it('401 khi đang dùng -> báo hết phiên; sai mật khẩu khi đăng nhập thì KHÔNG', async () => {
    const ended = vi.fn();
    const unsubscribe = onSessionEnded(ended);
    const { fn } = fakeFetch(error('INVALID_CREDENTIALS', 401), error('UNAUTHENTICATED', 401));
    const client = createHttpApiClient('/api/v1', fn);

    await client.login({ username: 'a', password: 'b' }).catch(() => undefined);
    expect(ended).not.toHaveBeenCalled();
    await client.myWallet().catch(() => undefined);
    expect(ended).toHaveBeenCalledTimes(1);
    unsubscribe();
  });

  it('bỏ tham số rỗng khỏi query string', async () => {
    const { fn, calls } = fakeFetch(json(200, { items: [], hasMore: false, nextBefore: null }));
    await createHttpApiClient('/api/v1', fn).myTransactions({ type: 'THANH_TOAN', limit: 20 });

    expect(calls[0]?.url).toBe('/api/v1/me/transactions?type=THANH_TOAN&limit=20');
  });

  it('gợi ý biển số gửi q và parked', async () => {
    const { fn, calls } = fakeFetch(json(200, [{ plateNumber: '29A12345', vehicleTypeCode: 'XE_MAY', parked: true }]));
    const result = await createHttpApiClient('/api/v1', fn).suggestPlates('2345', true);

    expect(calls[0]?.url).toBe('/api/v1/guard/plates?q=2345&parked=true');
    expect(result[0]?.parked).toBe(true);
  });

  it('lỗi mạng và response không phải JSON có thông báo tiếng Việt', async () => {
    const network = vi.fn(async () => {
      throw new TypeError('Failed to fetch');
    }) as unknown as typeof fetch;
    const offline = (await createHttpApiClient('/api/v1', network).feeRules().catch((e: unknown) => e)) as ApiError;
    expect(offline.code).toBe('NETWORK_ERROR');

    const { fn } = fakeFetch(new Response('<html>Bad Gateway</html>', { status: 502 }));
    const proxy = (await createHttpApiClient('/api/v1', fn).feeRules().catch((e: unknown) => e)) as ApiError;
    expect(proxy.status).toBe(502);
    expect(proxy.message).toBe('Có lỗi xảy ra. Vui lòng thử lại.');
  });
});
