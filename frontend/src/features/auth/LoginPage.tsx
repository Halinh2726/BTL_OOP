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
      <p className="mt-5 text-center text-sm text-slate-600">
        Sinh viên chưa có tài khoản?{' '}
        <Link to="/register" className="font-semibold text-blue-700 hover:underline">
          Đăng ký
        </Link>
      </p>
    </AuthLayout>
  );
}
