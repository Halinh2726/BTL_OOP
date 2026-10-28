import type { ReactNode } from 'react';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { HOME_BY_ROLE, useSession } from '../features/auth/session';
import type { LoginLocationState } from '../features/auth/LoginPage';
import type { Role } from '../shared/types/api';

/**
 * Chặn trang theo vai trò. Đây chỉ là trải nghiệm người dùng — backend vẫn kiểm tra quyền ở MỌI request,
 * không dựa vào việc giao diện ẩn trang.
 */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { state } = useSession();
  const location = useLocation();

  if (state.status !== 'authenticated') {
    const loginState: LoginLocationState = { from: location.pathname };
    return <Navigate to="/login" replace state={loginState} />;
  }
  if (state.me.user.role !== role) {
    return <ForbiddenPage home={HOME_BY_ROLE[state.me.user.role]} />;
  }
  return <>{children}</>;
}

function ForbiddenPage({ home }: { home: string }) {
  return (
    <div className="mx-auto max-w-md rounded-xl bg-white p-8 text-center shadow-sm ring-1 ring-slate-200">
      <p className="text-5xl font-bold text-slate-300">403</p>
      <h1 className="mt-2 text-xl font-semibold">Không có quyền</h1>
      <p className="mt-1 text-slate-500">Tài khoản của bạn không được truy cập trang này.</p>
      <Link to={home} className="mt-5 inline-block font-semibold text-blue-700 hover:underline">
        Về trang của tôi
      </Link>
    </div>
  );
}
