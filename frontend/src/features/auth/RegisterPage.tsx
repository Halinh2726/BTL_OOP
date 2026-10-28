import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { api, errorMessage, isApiError } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import { useFieldErrors } from '../../shared/hooks/useFieldErrors';
import { AuthLayout } from './AuthLayout';
import type { LoginLocationState } from './LoginPage';

/** Cùng quy tắc với RegisterStudentRequest của backend — backend vẫn kiểm tra lại. */
const schema = z
  .object({
    studentCode: z
      .string()
      .trim()
      .min(1, 'Vui lòng nhập MSSV')
      .transform((value) => value.toUpperCase())
      .pipe(z.string().regex(/^[A-Z0-9]{1,20}$/, 'MSSV chỉ gồm chữ và số, tối đa 20 ký tự')),
    fullName: z.string().trim().min(1, 'Vui lòng nhập họ tên').max(100, 'Họ tên tối đa 100 ký tự'),
    username: z
      .string()
      .min(1, 'Vui lòng nhập tên đăng nhập')
      .regex(/^[a-z0-9._]{4,50}$/, 'Tên đăng nhập 4–50 ký tự, chỉ gồm chữ thường, số, dấu chấm, gạch dưới'),
    password: z.string().min(8, 'Mật khẩu từ 8 đến 72 ký tự').max(72, 'Mật khẩu từ 8 đến 72 ký tự'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Mật khẩu nhập lại không khớp',
  });

type RegisterInput = z.input<typeof schema>;
type RegisterForm = z.output<typeof schema>;

const FIELDS = ['studentCode', 'fullName', 'username', 'password'] as const;

export function RegisterPage() {
  const navigate = useNavigate();
  const form = useForm<RegisterInput, unknown, RegisterForm>({ resolver: zodResolver(schema) });
  const applyFieldErrors = useFieldErrors(form.setError, FIELDS);

  const register = useMutation({
    mutationFn: ({ studentCode, fullName, username, password }: RegisterForm) =>
      api.registerStudent({ studentCode, fullName, username, password }),
    onSuccess: (student) => {
      const state: LoginLocationState = { registered: student.username };
      navigate('/login', { replace: true, state });
    },
    onError: (error) => {
      applyFieldErrors(error);
      if (isApiError(error) && error.code === 'STUDENT_CODE_EXISTS') {
        form.setError('studentCode', { type: 'server', message: error.message });
      }
      if (isApiError(error) && error.code === 'USERNAME_EXISTS') {
        form.setError('username', { type: 'server', message: error.message });
      }
    },
  });

  const { errors } = form.formState;
  const generalError =
    register.isError &&
    !(isApiError(register.error) && ['VALIDATION_ERROR', 'STUDENT_CODE_EXISTS', 'USERNAME_EXISTS'].includes(register.error.code));

  return (
    <AuthLayout title="Đăng ký tài khoản sinh viên" subtitle="Sau khi đăng ký, đăng nhập để thêm xe và xem điểm">
      <form className="space-y-4" noValidate onSubmit={form.handleSubmit((values) => register.mutate(values))}>
        {generalError && <Alert tone="error">{errorMessage(register.error)}</Alert>}
        <TextField label="Mã sinh viên (MSSV)" placeholder="B21DCCN001" autoFocus error={errors.studentCode?.message} {...form.register('studentCode')} />
        <TextField label="Họ và tên" autoComplete="name" error={errors.fullName?.message} {...form.register('fullName')} />
        <TextField
          label="Tên đăng nhập"
          autoComplete="username"
          hint="Chữ thường, số, dấu chấm, gạch dưới (4–50 ký tự)"
          error={errors.username?.message}
          {...form.register('username')}
        />
        <TextField label="Mật khẩu" type="password" autoComplete="new-password" error={errors.password?.message} {...form.register('password')} />
        <TextField
          label="Nhập lại mật khẩu"
          type="password"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
          {...form.register('confirmPassword')}
        />
        <Button type="submit" size="lg" className="w-full" loading={register.isPending}>
          Đăng ký
        </Button>
      </form>
      <p className="mt-5 text-center text-sm text-slate-600">
        Đã có tài khoản?{' '}
        <Link to="/login" className="font-semibold text-blue-700 hover:underline">
          Đăng nhập
        </Link>
      </p>
    </AuthLayout>
  );
}
