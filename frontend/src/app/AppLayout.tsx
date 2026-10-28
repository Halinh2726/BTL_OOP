import { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { useCurrentUser, useSession } from '../features/auth/session';
import { Button } from '../shared/components/Button';
import { Logo } from '../shared/components/Logo';
import { MockBadge } from '../shared/components/MockBadge';
import { ROLE_LABELS } from '../shared/utils/format';

/** Khung sau khi đăng nhập: thanh trên cùng có tên, vai trò và nút đăng xuất. */
export function AppLayout() {
  const { user } = useCurrentUser();
  const { signOut } = useSession();
  const [signingOut, setSigningOut] = useState(false);

  async function handleSignOut() {
    setSigningOut(true);
    await signOut();
  }

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-4 py-3">
          <div className="flex min-w-0 items-center gap-3">
            <Logo className="h-10 w-10" />
            <div className="min-w-0">
              <p className="truncate text-sm font-bold text-slate-900 sm:text-base">
                Gửi xe bằng điểm <MockBadge />
              </p>
              <p className="truncate text-xs text-slate-500">Hệ thống thanh toán phí gửi xe</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-semibold text-slate-900">{user.fullName}</p>
              <p className="text-xs text-slate-500">{ROLE_LABELS[user.role]}</p>
            </div>
            <Button variant="secondary" size="sm" onClick={() => void handleSignOut()} loading={signingOut}>
              Đăng xuất
            </Button>
          </div>
        </div>
        <div className="border-t border-slate-100 px-4 py-1.5 text-xs text-slate-600 sm:hidden">
          {user.fullName} · {ROLE_LABELS[user.role]}
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
