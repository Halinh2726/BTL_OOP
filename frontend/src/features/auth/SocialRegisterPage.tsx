import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, Navigate } from 'react-router-dom';
import { z } from 'zod';
import { api, errorMessage, isApiError } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import { LoadingBlock } from '../../shared/components/Spinner';
import { useFieldErrors } from '../../shared/hooks/useFieldErrors';
import type { SocialAuthPending } from '../../shared/types/api';
import { AuthLayout } from './AuthLayout';
import { SocialSignInOptions } from './SocialSignInOptions';
import { HOME_BY_ROLE, useSession } from './session';
import { SOCIAL_PROVIDER_LABEL, useSocialAuthStatus } from './socialAuth';
import { studentProfileSchema } from './studentProfileSchema';

const FIELDS = ['studentCode', 'fullName', 'username'] as const;
type StudentProfile = z.infer<typeof studentProfileSchema>;

export function SocialRegisterPage() {
  const { state } = useSession();
  const status = useSocialAuthStatus();

  if (state.status === 'authenticated') return <Navigate to={HOME_BY_ROLE[state.me.user.role]} replace />;

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
        <Link to="/login" className="font-semibold text-blue-700 hover:underline">Đăng nhập để liên kết</Link>
      </p>
    </AuthLayout>
  );
}

function SocialRegisterForm({ pending }: { pending: SocialAuthPending }) {
  const { signedIn } = useSession();
  const form = useForm<StudentProfile>({
    resolver: zodResolver(studentProfileSchema),
    defaultValues: { fullName: pending.fullName?.slice(0, 100) ?? '' },
  });
  const applyFieldErrors = useFieldErrors(form.setError, FIELDS);
  const register = useMutation({
    mutationFn: (values: StudentProfile) => api.registerWithSocial({ ...values, id: pending.id }),
    onSuccess: signedIn,
    onError: (error) => {
      applyFieldErrors(error);
      if (isApiError(error) && error.code === 'USERNAME_EXISTS') {
        form.setError('username', { type: 'server', message: error.message });
      }
    },
  });
  const { errors } = form.formState;
  const code = isApiError(register.error) ? register.error.code : undefined;
  const accountExists = code === 'STUDENT_CODE_EXISTS' || code === 'EMAIL_EXISTS';
  const generalError = register.isError && code !== 'VALIDATION_ERROR' && code !== 'USERNAME_EXISTS';

  return (
    <form className="space-y-4" noValidate onSubmit={form.handleSubmit((values) => register.mutate(values))}>
      <p className="break-all text-sm text-slate-600">
        {SOCIAL_PROVIDER_LABEL[pending.provider]}{pending.label && ` · ${pending.label}`}
      </p>
      {generalError && (
        <Alert tone="error">
          {errorMessage(register.error)}
          {accountExists && ' Nếu đây là tài khoản của bạn, hãy đăng nhập bằng cách cũ rồi liên kết trong trang Tài khoản.'}
        </Alert>
      )}
      <TextField label="Mã sinh viên (MSSV)" placeholder="B21DCCN001" autoFocus error={errors.studentCode?.message} {...form.register('studentCode')} />
      <TextField label="Họ và tên" autoComplete="name" error={errors.fullName?.message} {...form.register('fullName')} />
      <TextField label="Tên đăng nhập" autoComplete="username" error={errors.username?.message} {...form.register('username')} />
      <Button type="submit" size="lg" className="w-full" loading={register.isPending} disabled={code === 'SOCIAL_FLOW_EXPIRED'}>
        Tạo tài khoản
      </Button>
      {code === 'SOCIAL_FLOW_EXPIRED' && (
        <SocialSignInOptions />
      )}
    </form>
  );
}
