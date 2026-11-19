import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { api, errorMessage, isApiError, isMockMode } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import { newPasswordSchema } from '../account/ChangePasswordForm';
import { AuthLayout } from './AuthLayout';
import type { LoginLocationState } from './LoginPage';

const emailSchema = z.object({
  email: z.string().trim().min(1, 'Vui lòng nhập email').email('Email không đúng định dạng'),
});

const confirmSchema = z
  .object({
    code: z.string().trim().regex(/^[0-9]{6}$/, 'Mã xác nhận gồm 6 chữ số'),
    newPassword: newPasswordSchema,
    confirmPassword: z.string(),
  })
  .superRefine((values, ctx) => {
    if (values.newPassword !== values.confirmPassword) {
      ctx.addIssue({ code: 'custom', path: ['confirmPassword'], message: 'Mật khẩu nhập lại không khớp' });
    }
  });

type EmailForm = z.infer<typeof emailSchema>;
type ConfirmForm = z.infer<typeof confirmSchema>;

/**
 * Quên mật khẩu — 2 bước: (1) nhập email đã khai báo → nhận mã 6 số (hiệu lực 10 phút);
 * (2) nhập mã + mật khẩu mới. Không có email thì mang thẻ sinh viên tới quản lý để được cấp mật khẩu tạm.
 */
export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState<string | null>(null);
  const emailForm = useForm<EmailForm>({ resolver: zodResolver(emailSchema), defaultValues: { email: '' } });
  const confirmForm = useForm<ConfirmForm>({
    resolver: zodResolver(confirmSchema),
    defaultValues: { code: '', newPassword: '', confirmPassword: '' },
  });

  const request = useMutation({
    mutationFn: (values: EmailForm) => api.requestPasswordReset(values.email),
    onSuccess: (_, values) => setEmail(values.email.trim()),
  });

  const confirm = useMutation({
    mutationFn: (values: ConfirmForm) =>
      api.confirmPasswordReset({ email: email ?? '', code: values.code, newPassword: values.newPassword }),
    onSuccess: () => {
      const state: LoginLocationState = { passwordReset: true };
      navigate('/login', { replace: true, state });
    },
    onError: (error) => {
      if (!isApiError(error)) return;
      if (error.code.startsWith('RESET_CODE_')) confirmForm.setError('code', { type: 'server', message: error.message });
      if (['PASSWORD_REUSED', 'PASSWORD_POLICY'].includes(error.code)) {
        confirmForm.setError('newPassword', { type: 'server', message: error.message });
      }
    },
  });

  const confirmHandled =
    isApiError(confirm.error) && (confirm.error.code.startsWith('RESET_CODE_') || ['PASSWORD_REUSED', 'PASSWORD_POLICY'].includes(confirm.error.code));

  return (
    <AuthLayout title="Quên mật khẩu" subtitle={email ? 'Bước 2/2 — nhập mã và mật khẩu mới' : 'Bước 1/2 — nhận mã qua email'}>
      {email === null ? (
        <form className="space-y-4" noValidate onSubmit={emailForm.handleSubmit((values) => request.mutate(values))}>
          {request.isError && <Alert tone="error">{errorMessage(request.error)}</Alert>}
          <TextField
            label="Email đã khai báo trong tài khoản"
            type="email"
            autoComplete="email"
            autoFocus
            error={emailForm.formState.errors.email?.message}
            {...emailForm.register('email')}
          />
          <Button type="submit" size="lg" className="w-full" loading={request.isPending}>
            Gửi mã xác nhận
          </Button>
          <p className="text-sm text-slate-500">
            Chưa khai báo email? <strong>Sinh viên</strong> mang thẻ sinh viên tới quầy quản lý; <strong>bảo vệ</strong> báo
            quản lý bãi xe — quản lý sẽ cấp mật khẩu tạm, lần đăng nhập sau bạn tự đặt mật khẩu mới.
          </p>
        </form>
      ) : (
        <form className="space-y-4" noValidate onSubmit={confirmForm.handleSubmit((values) => confirm.mutate(values))}>
          <Alert tone="info">
            {request.data?.message}
            {isMockMode && <> (Chế độ mock: mã luôn là <strong>123456</strong>.)</>}
          </Alert>
          {confirm.isError && !confirmHandled && <Alert tone="error">{errorMessage(confirm.error)}</Alert>}
          <TextField
            label="Mã xác nhận (6 số)"
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
            className="font-mono tracking-[0.4em]"
            maxLength={6}
            error={confirmForm.formState.errors.code?.message}
            {...confirmForm.register('code')}
          />
          <TextField
            label="Mật khẩu mới"
            type="password"
            autoComplete="new-password"
            hint="8–72 ký tự"
            error={confirmForm.formState.errors.newPassword?.message}
            {...confirmForm.register('newPassword')}
          />
          <TextField
            label="Nhập lại mật khẩu mới"
            type="password"
            autoComplete="new-password"
            error={confirmForm.formState.errors.confirmPassword?.message}
            {...confirmForm.register('confirmPassword')}
          />
          <Button type="submit" size="lg" className="w-full" loading={confirm.isPending}>
            Đặt lại mật khẩu
          </Button>
          <button
            type="button"
            className="w-full text-sm font-semibold text-blue-700 hover:underline"
            onClick={() => {
              setEmail(null);
              confirm.reset();
              request.reset();
            }}
          >
            Nhập email khác / gửi lại mã
          </button>
        </form>
      )}
      <p className="mt-5 text-center text-sm text-slate-600">
        <Link to="/login" className="font-semibold text-blue-700 hover:underline">
          ← Quay lại đăng nhập
        </Link>
      </p>
    </AuthLayout>
  );
}
