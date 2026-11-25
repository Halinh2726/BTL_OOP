import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, Navigate } from 'react-router-dom';
import { z } from 'zod';
import { api, errorMessage, isApiError } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { PasswordField, TextField } from '../../shared/components/Field';
import { LoadingBlock } from '../../shared/components/Spinner';
import { useFieldErrors } from '../../shared/hooks/useFieldErrors';
import type { SocialAuthPending } from '../../shared/types/api';
import { AuthLayout } from './AuthLayout';
import type { LoginLocationState } from './LoginPage';
import { SocialSignInOptions } from './SocialSignInOptions';
import { HOME_BY_ROLE, useSession } from './session';
import { SOCIAL_PROVIDER_LABEL, useSocialAuthStatus } from './socialAuth';
import { studentProfileSchema } from './studentProfileSchema';

const schema = studentProfileSchema
  .extend({
    password: z.string()
      .refine((value) => !value || (value.length >= 8 && Boolean(value.trim())), 'Mật khẩu phải có ít nhất 8 ký tự')
      .refine((value) => new TextEncoder().encode(value).length <= 72, 'Mật khẩu quá dài, tối đa 72 byte UTF-8'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Mật khẩu nhập lại không khớp',
  });

const FIELDS = ['studentCode', 'fullName', 'username', 'password'] as const;
type SocialRegisterFormValues = z.infer<typeof schema>;

export function SocialRegisterPage() {
  const { state } = useSession();
  const status = useSocialAuthStatus();

  if (state.status === 'authenticated') return <Navigate to={HOME_BY_ROLE[state.me.user.role]} replace />;

  const socialLinkId = status.data?.pending?.id;
  const loginState: LoginLocationState = { socialLinkId };
  return (
    <AuthLayout title="Hoàn tất đăng ký sinh viên">
      {status.isPending ? (
        <LoadingBlock label="Đang kiểm tra phiên xác thực..." />
      ) : status.isError ? (
        <div className="space-y-4">
          <Alert tone="error">{errorMessage(status.error)}</Alert>
          <Button onClick={() => void status.refetch()}>Thử lại</Button>
        </div>
      ) : status.data.pending ? (
        <SocialRegisterForm key={status.data.pending.id} pending={status.data.pending} />
      ) : (
        <>
          <Alert tone="warning">Phiên xác thực đã hết hạn. Vui lòng chọn lại nhà cung cấp.</Alert>
          <SocialSignInOptions />
        </>
      )}
      <p className="mt-5 text-center text-sm text-slate-600">
        Đã có tài khoản gửi xe?{' '}
        <Link to="/login" state={loginState} className="font-semibold text-blue-700 hover:underline">
          {socialLinkId ? 'Đăng nhập để liên kết' : 'Đăng nhập'}
        </Link>
      </p>
    </AuthLayout>
  );
}

function SocialRegisterForm({ pending }: { pending: SocialAuthPending }) {
  const { signedIn } = useSession();
  const form = useForm<SocialRegisterFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: pending.fullName?.slice(0, 100) ?? '', password: '', confirmPassword: '' },
  });
  const applyFieldErrors = useFieldErrors(form.setError, FIELDS);
  const register = useMutation({
    mutationFn: ({ studentCode, fullName, username, password }: SocialRegisterFormValues) =>
      api.registerWithSocial({ id: pending.id, studentCode, fullName, username, ...(password ? { password } : {}) }),
    onSuccess: signedIn,
    onError: (error) => {
      applyFieldErrors(error);
      if (isApiError(error) && error.code === 'USERNAME_EXISTS') {
        form.setError('username', { type: 'server', message: error.message });
      }
      if (isApiError(error) && error.code === 'PASSWORD_POLICY') {
        form.setError('password', { type: 'server', message: error.message });
      }
    },
  });
  const { errors } = form.formState;
  const code = isApiError(register.error) ? register.error.code : undefined;
  const accountExists = code === 'STUDENT_CODE_EXISTS' || code === 'EMAIL_EXISTS';
  const generalError = register.isError && code !== 'VALIDATION_ERROR' && code !== 'USERNAME_EXISTS' && code !== 'PASSWORD_POLICY';

  return (
    <form className="space-y-4" noValidate onSubmit={form.handleSubmit((values) => register.mutate(values))}>
      <p className="break-all text-sm text-slate-600">
        {SOCIAL_PROVIDER_LABEL[pending.provider]}{pending.label && ` · ${pending.label}`}
      </p>
      {generalError && (
        <Alert tone="error">
          {errorMessage(register.error)}
          {accountExists && ' Nếu đây là tài khoản của bạn, hãy chọn Đăng nhập để liên kết bên dưới.'}
        </Alert>
      )}
      <TextField label="Mã sinh viên (MSSV)" placeholder="B21DCCN001" autoFocus error={errors.studentCode?.message} {...form.register('studentCode')} />
      <TextField label="Họ và tên" autoComplete="name" error={errors.fullName?.message} {...form.register('fullName')} />
      <TextField label="Tên đăng nhập" autoComplete="username" error={errors.username?.message} {...form.register('username')} />
      <PasswordField
        label="Mật khẩu (tùy chọn)"
        autoComplete="new-password"
        showStrength
        hint="Đặt mật khẩu để đăng nhập thêm bằng tên đăng nhập."
        error={errors.password?.message}
        {...form.register('password')}
      />
      <PasswordField
        label="Nhập lại mật khẩu"
        autoComplete="new-password"
        error={errors.confirmPassword?.message}
        {...form.register('confirmPassword')}
      />
      <Button type="submit" size="lg" className="w-full" loading={register.isPending} disabled={code === 'SOCIAL_FLOW_EXPIRED'}>
        Tạo tài khoản
      </Button>
      {code === 'SOCIAL_FLOW_EXPIRED' && (
        <SocialSignInOptions />
      )}
    </form>
  );
}
