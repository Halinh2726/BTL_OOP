import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { api, errorMessage, isApiError, isMockMode } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { PasswordField, TextField } from '../../shared/components/Field';
import { newPasswordSchema } from '../account/ChangePasswordForm';
import { AuthLayout } from './AuthLayout';
import type { LoginLocationState } from './LoginPage';

const identifierSchema = z.object({
  identifier: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập email, tên đăng nhập hoặc MSSV')
    .max(100, 'Thông tin nhận dạng tối đa 100 ký tự')
    .refine(
      (value) => !value.includes('@') || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value),
      'Email không đúng định dạng',
    ),
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

type IdentifierForm = z.infer<typeof identifierSchema>;
type ConfirmForm = z.infer<typeof confirmSchema>;

function formatMmSs(totalSeconds: number): string {
  const mins = Math.floor(totalSeconds / 60);
  const secs = totalSeconds % 60;
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

/**
 * Quên mật khẩu — 2 bước: (1) nhập Email / Tên đăng nhập / MSSV → nhận mã 6 số qua email đã đăng ký (10 phút);
 * (2) nhập mã + mật khẩu mới. Không có email thì mang thẻ tới quản lý để cấp mật khẩu tạm.
 */
export function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState<string | null>(null);
  const [resendSeconds, setResendSeconds] = useState(0);
  const [otpSeconds, setOtpSeconds] = useState(0);

  const idForm = useForm<IdentifierForm>({ resolver: zodResolver(identifierSchema), defaultValues: { identifier: '' } });
  const confirmForm = useForm<ConfirmForm>({
    resolver: zodResolver(confirmSchema),
    defaultValues: { code: '', newPassword: '', confirmPassword: '' },
  });

  useEffect(() => {
    if (resendSeconds <= 0 && otpSeconds <= 0) return;
    const timer = window.setInterval(() => {
      setResendSeconds((prev) => (prev > 0 ? prev - 1 : 0));
      setOtpSeconds((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [resendSeconds, otpSeconds]);

  const request = useMutation({
    mutationFn: (values: IdentifierForm) => api.requestPasswordReset(values.identifier),
    onSuccess: (_, values) => {
      confirmForm.reset({ code: '', newPassword: '', confirmPassword: '' });
      setIdentifier(values.identifier.trim());
      setResendSeconds(60);
      setOtpSeconds(600);
    },
  });

  const confirm = useMutation({
    mutationFn: (values: ConfirmForm) => {
      const value = identifier ?? '';
      return api.confirmPasswordReset({
        identifier: value,
        ...(value.includes('@') ? { email: value } : {}),
        code: values.code,
        newPassword: values.newPassword,
      });
    },
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
    isApiError(confirm.error) &&
    (confirm.error.code.startsWith('RESET_CODE_') || ['PASSWORD_REUSED', 'PASSWORD_POLICY'].includes(confirm.error.code));

  return (
    <AuthLayout
      title="Quên mật khẩu"
      subtitle={identifier ? 'Bước 2/2 — nhập mã OTP và mật khẩu mới' : 'Bước 1/2 — xác định tài khoản & nhận mã OTP'}
    >
      {identifier === null ? (
        <form key="step-request" className="space-y-4" noValidate onSubmit={idForm.handleSubmit((values) => request.mutate(values))}>
          {request.isError && <Alert tone="error">{errorMessage(request.error)}</Alert>}
          <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-700 space-y-1">
            <p className="font-semibold text-slate-800">Căn cứ khôi phục mật khẩu:</p>
            <p>1. Qua <strong>Email đã khai báo</strong> (nhập Email, Tên đăng nhập hoặc MSSV để nhận mã OTP 6 số, hiệu lực 10 phút).</p>
            <p>2. Nếu <strong>chưa khai báo email</strong>: mang thẻ tới quầy Quản lý để nhận mật khẩu tạm dùng 1 lần.</p>
          </div>
          <TextField
            label="Email, Tên đăng nhập hoặc MSSV"
            placeholder="sv01@ptit.edu.vn hoặc sv01 hoặc B21DCCN001"
            autoComplete="username"
            autoFocus
            error={idForm.formState.errors.identifier?.message}
            {...idForm.register('identifier')}
          />
          <Button type="submit" size="lg" className="w-full" loading={request.isPending}>
            Gửi mã xác nhận
          </Button>
        </form>
      ) : (
        <form key="step-confirm" className="space-y-4" noValidate onSubmit={confirmForm.handleSubmit((values) => confirm.mutate(values))}>
          <Alert tone="info">
            <p>{request.data?.message}</p>
            <p className="mt-1 text-xs">
              Tài khoản: <strong className="font-mono">{identifier}</strong> · Thời gian hiệu lực còn{' '}
              <strong className="font-mono">{formatMmSs(otpSeconds)}</strong>
              {isMockMode && <> · (Chế độ mock: mã luôn là <strong>123456</strong>.)</>}
            </p>
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
          <PasswordField
            label="Mật khẩu mới"
            autoComplete="new-password"
            showStrength
            hint="8–72 ký tự, khác mật khẩu cũ"
            error={confirmForm.formState.errors.newPassword?.message}
            {...confirmForm.register('newPassword')}
          />
          <PasswordField
            label="Nhập lại mật khẩu mới"
            autoComplete="new-password"
            error={confirmForm.formState.errors.confirmPassword?.message}
            {...confirmForm.register('confirmPassword')}
          />
          <Button type="submit" size="lg" className="w-full" loading={confirm.isPending}>
            Đặt lại mật khẩu
          </Button>
          <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <button
              type="button"
              disabled={resendSeconds > 0 || request.isPending}
              className="font-semibold text-blue-700 hover:underline disabled:cursor-not-allowed disabled:text-slate-400"
              onClick={() => request.mutate({ identifier })}
            >
              {resendSeconds > 0 ? `Gửi lại mã sau ${resendSeconds}s` : 'Gửi lại mã OTP'}
            </button>
            <button
              type="button"
              className="font-semibold text-slate-600 hover:underline"
              onClick={() => {
                setIdentifier(null);
                setResendSeconds(0);
                setOtpSeconds(0);
                confirmForm.reset({ code: '', newPassword: '', confirmPassword: '' });
                confirm.reset();
                request.reset();
              }}
            >
              Đổi tài khoản / email khác
            </button>
          </div>
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
