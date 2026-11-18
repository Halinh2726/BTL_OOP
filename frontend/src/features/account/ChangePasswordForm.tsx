import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api, errorMessage, isApiError } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { TextField } from '../../shared/components/Field';
import { useFieldErrors } from '../../shared/hooks/useFieldErrors';

/** Quy tắc mật khẩu mới — cùng PasswordPolicy của backend (8–72 ký tự). */
export const newPasswordSchema = z
  .string()
  .min(8, 'Mật khẩu mới tối thiểu 8 ký tự')
  .max(72, 'Mật khẩu mới tối đa 72 ký tự');

const schema = z
  .object({
    currentPassword: z.string().min(1, 'Vui lòng nhập mật khẩu hiện tại'),
    newPassword: newPasswordSchema,
    confirmPassword: z.string(),
  })
  .superRefine((values, ctx) => {
    if (values.newPassword !== values.confirmPassword) {
      ctx.addIssue({ code: 'custom', path: ['confirmPassword'], message: 'Mật khẩu nhập lại không khớp' });
    }
    if (values.newPassword && values.newPassword === values.currentPassword) {
      ctx.addIssue({ code: 'custom', path: ['newPassword'], message: 'Mật khẩu mới phải khác mật khẩu hiện tại' });
    }
  });

type Form = z.infer<typeof schema>;
const FIELDS = ['currentPassword', 'newPassword'] as const;

interface ChangePasswordFormProps {
  /** Nhãn ô mật khẩu hiện tại — với mật khẩu tạm thì ghi rõ "Mật khẩu tạm". */
  currentLabel?: string;
  submitLabel?: string;
  onChanged: () => void;
}

/**
 * Form đổi mật khẩu dùng chung cho trang Tài khoản và màn hình bắt buộc đổi mật khẩu tạm.
 * Đổi xong: phiên này dùng tiếp, các phiên khác của tài khoản bị đăng xuất (backend lo).
 */
export function ChangePasswordForm({
  currentLabel = 'Mật khẩu hiện tại',
  submitLabel = 'Đổi mật khẩu',
  onChanged,
}: ChangePasswordFormProps) {
  const form = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });
  const applyFieldErrors = useFieldErrors(form.setError, FIELDS);

  const change = useMutation({
    mutationFn: (values: Form) =>
      api.changePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword }),
    onSuccess: () => {
      form.reset();
      onChanged();
    },
    onError: (error) => {
      if (applyFieldErrors(error)) return;
      if (isApiError(error) && error.code === 'CURRENT_PASSWORD_INCORRECT') {
        form.setError('currentPassword', { type: 'server', message: error.message });
      } else if (isApiError(error) && ['PASSWORD_REUSED', 'PASSWORD_POLICY'].includes(error.code)) {
        form.setError('newPassword', { type: 'server', message: error.message });
      }
    },
  });

  const { errors } = form.formState;
  const handled = isApiError(change.error) &&
    ['VALIDATION_ERROR', 'CURRENT_PASSWORD_INCORRECT', 'PASSWORD_REUSED', 'PASSWORD_POLICY'].includes(change.error.code);

  return (
    <form className="space-y-4" noValidate onSubmit={form.handleSubmit((values) => change.mutate(values))}>
      {change.isError && !handled && <Alert tone="error">{errorMessage(change.error)}</Alert>}
      <TextField
        label={currentLabel}
        type="password"
        autoComplete="current-password"
        error={errors.currentPassword?.message}
        {...form.register('currentPassword')}
      />
      <TextField
        label="Mật khẩu mới"
        type="password"
        autoComplete="new-password"
        hint="8–72 ký tự, khác mật khẩu hiện tại"
        error={errors.newPassword?.message}
        {...form.register('newPassword')}
      />
      <TextField
        label="Nhập lại mật khẩu mới"
        type="password"
        autoComplete="new-password"
        error={errors.confirmPassword?.message}
        {...form.register('confirmPassword')}
      />
      <Button type="submit" loading={change.isPending}>
        {submitLabel}
      </Button>
    </form>
  );
}
