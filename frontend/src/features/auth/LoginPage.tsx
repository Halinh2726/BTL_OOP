import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { z } from 'zod';
import { api, errorMessage } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import { AuthLayout } from './AuthLayout';
import { HOME_BY_ROLE, useSession } from './session';

const schema = z.object({
  username: z.string().trim().min(1, 'Vui lòng nhập tên đăng nhập'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
});

type LoginForm = z.infer<typeof schema>;

export interface LoginLocationState {
  from?: string;
  registered?: string;
}

const DEMO_ACCOUNTS = [
  { role: 'Chủ tài khoản', username: 'chutk01', password: 'Chutk@123', desc: 'Xem doanh thu bãi xe' },
  { role: 'Quản lý', username: 'quanly01', password: 'Quanly@123', desc: 'Thủ quỹ & quản lý nhân sự' },
  { role: 'Bảo vệ', username: 'baove01', password: 'Baove@123', desc: 'Quét mã QR xe vào/ra' },
  { role: 'Sinh viên', username: 'sv01', password: 'MatKhau@123', desc: 'Ví điểm & đăng ký xe' },
] as const;

export function LoginPage() {
  const { state, signedIn } = useSession();
  const location = useLocation();
  const navigationState = (location.state ?? {}) as LoginLocationState;
  const form = useForm<LoginForm>({ resolver: zodResolver(schema), defaultValues: { username: navigationState.registered ?? '' } });
  const login = useMutation({ mutationFn: api.login, onSuccess: signedIn });

  if (state.status === 'authenticated') {
    const home = HOME_BY_ROLE[state.me.user.role];
    const target = navigationState.from?.startsWith(home) ? navigationState.from : home;
    return <Navigate to={target} replace />;
  }

  const notice = state.status === 'anonymous' ? state.notice : undefined;
  const { errors } = form.formState;

  const handleSelectDemo = (acc: typeof DEMO_ACCOUNTS[number]) => {
    form.setValue('username', acc.username);
    form.setValue('password', acc.password);
    login.mutate({ username: acc.username, password: acc.password });
  };

  return (
    <AuthLayout title="Đăng nhập" subtitle="Dùng tài khoản được cấp hoặc tài khoản sinh viên đã đăng ký">
      <form className="space-y-4" noValidate onSubmit={form.handleSubmit((values) => login.mutate(values))}>
        {navigationState.registered && !login.isError && (
          <Alert tone="success">Đăng ký thành công. Mời bạn đăng nhập.</Alert>
        )}
        {notice && !login.isError && <Alert tone="warning">{notice}</Alert>}
        {login.isError && <Alert tone="error">{errorMessage(login.error)}</Alert>}

        <TextField
          label="Tên đăng nhập"
          autoComplete="username"
          autoFocus
          error={errors.username?.message}
          {...form.register('username')}
        />
        <TextField
          label="Mật khẩu"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...form.register('password')}
        />
        <Button type="submit" size="lg" className="w-full" loading={login.isPending}>
          Đăng nhập
        </Button>
      </form>

      {/* Danh sách tài khoản demo bấm 1 click để đăng nhập ngay */}
      <div className="mt-6 rounded-lg border border-slate-200 bg-slate-50 p-3">
        <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          Tài khoản mẫu (Bấm để đăng nhập nhanh)
        </p>
        <div className="mt-2 grid grid-cols-2 gap-2">
          {DEMO_ACCOUNTS.map((acc) => (
            <button
              key={acc.username}
              type="button"
              onClick={() => handleSelectDemo(acc)}
              disabled={login.isPending}
              className="flex flex-col items-start rounded-md border border-slate-200 bg-white p-2 text-left transition hover:border-blue-500 hover:bg-blue-50/50"
            >
              <div className="flex w-full items-center justify-between">
                <span className="text-xs font-bold text-slate-800">{acc.role}</span>
                <span className="font-mono text-[10px] text-blue-600">{acc.username}</span>
              </div>
              <span className="mt-0.5 text-[11px] text-slate-500">{acc.desc}</span>
            </button>
          ))}
        </div>
      </div>

      <p className="mt-5 text-center text-sm text-slate-600">
        Sinh viên chưa có tài khoản?{' '}
        <Link to="/register" className="font-semibold text-blue-700 hover:underline">
          Đăng ký
        </Link>
      </p>
    </AuthLayout>
  );
}
