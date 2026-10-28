import type { ApiError } from './ApiError';

/**
 * Báo cho phần quản lý phiên biết phiên đã hết (401) hoặc tài khoản bị khóa giữa chừng,
 * để đưa người dùng về trang đăng nhập dù lỗi xảy ra ở bất kỳ màn hình nào.
 */
type Listener = (error: ApiError) => void;

const listeners = new Set<Listener>();

export function onSessionEnded(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function notifySessionEnded(error: ApiError): void {
  listeners.forEach((listener) => listener(error));
}

/** Các mã lỗi nghĩa là phiên không còn dùng được. */
export function endsSession(code: string): boolean {
  return code === 'UNAUTHENTICATED' || code === 'ACCOUNT_LOCKED';
}
