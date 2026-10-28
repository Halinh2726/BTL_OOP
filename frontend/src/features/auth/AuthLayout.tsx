import type { ReactNode } from 'react';
import { Logo } from '../../shared/components/Logo';
import { MockBadge } from '../../shared/components/MockBadge';

/** Khung chung cho trang đăng nhập và đăng ký. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-6 text-center">
          <div className="mb-3 flex justify-center">
            <Logo className="h-20 w-20 shadow" />
          </div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Học viện Công nghệ Bưu chính Viễn thông</p>
          <p className="text-sm font-medium uppercase tracking-wide text-blue-700">
            Hệ thống thanh toán phí gửi xe bằng điểm <MockBadge />
          </p>
          <h1 className="mt-1 text-2xl font-bold text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1 text-slate-500">{subtitle}</p>}
        </div>
        <div className="rounded-xl bg-white p-6 shadow-sm ring-1 ring-slate-200">{children}</div>
      </div>
    </main>
  );
}
