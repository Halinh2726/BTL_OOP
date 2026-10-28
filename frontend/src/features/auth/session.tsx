import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { api, isApiError } from '../../shared/api';
import { onSessionEnded } from '../../shared/api/sessionEvents';
import type { MeResponse, Role } from '../../shared/types/api';

export type SessionState =
  | { status: 'loading' }
  | { status: 'unreachable'; message: string }
  | { status: 'anonymous'; notice?: string }
  | { status: 'authenticated'; me: MeResponse };

interface SessionContextValue {
  state: SessionState;
  signedIn: (me: MeResponse) => void;
  signOut: () => Promise<void>;
  retry: () => void;
}

const SessionContext = createContext<SessionContextValue | null>(null);

/** Trang chủ của từng vai trò sau khi đăng nhập. */
export const HOME_BY_ROLE: Record<Role, string> = {
  SINH_VIEN: '/student',
  BAO_VE: '/guard',
  QUAN_LY: '/manager',
  CHU_TAI_KHOAN: '/owner',
};

/**
 * Phiên đăng nhập phía giao diện. Nguồn sự thật là cookie SESSION ở backend: khi tải trang gọi GET /me để
 * khôi phục; không lưu token hay thông tin người dùng vào localStorage.
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<SessionState>({ status: 'loading' });
  const stateRef = useRef(state);
  stateRef.current = state;

  const restore = useCallback(() => {
    setState({ status: 'loading' });
    api
      .me()
      .then((me) => setState({ status: 'authenticated', me }))
      .catch((error: unknown) => {
        if (isApiError(error) && error.status !== 0 && error.status < 500) {
          setState({ status: 'anonymous' });
        } else {
          setState({ status: 'unreachable', message: isApiError(error) ? error.message : 'Không kết nối được máy chủ.' });
        }
      });
  }, []);

  useEffect(restore, [restore]);

  // Phiên hết hạn hoặc tài khoản bị khóa khi đang dùng -> về trang đăng nhập kèm lời nhắn
  useEffect(
    () =>
      onSessionEnded((error) => {
        if (stateRef.current.status !== 'authenticated') return;
        queryClient.clear();
        setState({ status: 'anonymous', notice: error.message });
      }),
    [queryClient],
  );

  const signedIn = useCallback(
    (me: MeResponse) => {
      queryClient.clear();
      setState({ status: 'authenticated', me });
    },
    [queryClient],
  );

  const signOut = useCallback(async () => {
    try {
      await api.logout();
    } catch {
      // Phiên đã hết ở backend thì coi như đã đăng xuất
    }
    queryClient.clear();
    setState({ status: 'anonymous' });
  }, [queryClient]);

  const value = useMemo(() => ({ state, signedIn, signOut, retry: restore }), [state, signedIn, signOut, restore]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) throw new Error('useSession phải nằm trong SessionProvider');
  return context;
}

/** Người dùng đang đăng nhập — chỉ gọi trong trang đã qua RequireRole. */
export function useCurrentUser(): MeResponse {
  const { state } = useSession();
  if (state.status !== 'authenticated') throw new Error('Chưa đăng nhập');
  return state.me;
}
