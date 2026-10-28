import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api, errorMessage, isApiError } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { TextField } from '../../shared/components/Field';
import { useFieldErrors } from '../../shared/hooks/useFieldErrors';
import type { Guard } from '../../shared/types/api';

/** Cùng quy tắc với CreateGuardRequest của backend. */
const schema = z.object({
  fullName: z.string().trim().min(1, 'Vui lòng nhập họ tên').max(100, 'Họ tên tối đa 100 ký tự'),
  phone: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập số điện thoại')
    .regex(/^0[0-9]{9,10}$/, 'Số điện thoại gồm 10–11 chữ số, bắt đầu bằng 0'),
  username: z
    .string()
    .min(1, 'Vui lòng nhập tên đăng nhập')
    .regex(/^[a-z0-9._]{4,50}$/, 'Tên đăng nhập 4–50 ký tự, chỉ gồm chữ thường, số, dấu chấm, gạch dưới'),
  password: z.string().min(8, 'Mật khẩu từ 8 đến 72 ký tự').max(72, 'Mật khẩu từ 8 đến 72 ký tự'),
});

type Form = z.infer<typeof schema>;
const FIELDS = ['fullName', 'phone', 'username', 'password'] as const;

export function CreateGuardDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (guard: Guard) => void }) {
  const queryClient = useQueryClient();
  const form = useForm<Form>({ resolver: zodResolver(schema) });
  const applyFieldErrors = useFieldErrors(form.setError, FIELDS);

  const create = useMutation({
    mutationFn: api.createGuard,
    onSuccess: async (guard) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.guards });
      onCreated(guard);
    },
    onError: (error) => {
      applyFieldErrors(error);
      if (isApiError(error) && error.code === 'USERNAME_EXISTS') {
        form.setError('username', { type: 'server', message: error.message });
      }
    },
  });

  const { errors } = form.formState;
  const showGeneral =
    create.isError && !(isApiError(create.error) && ['VALIDATION_ERROR', 'USERNAME_EXISTS'].includes(create.error.code));

  return (
    <Dialog
      title="Tạo tài khoản bảo vệ"
      onClose={onClose}
      busy={create.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={create.isPending}>
            Hủy
          </Button>
          <Button type="submit" form="create-guard" loading={create.isPending}>
            Tạo tài khoản
          </Button>
        </>
      }
    >
      <form id="create-guard" className="space-y-4" noValidate onSubmit={form.handleSubmit((values) => create.mutate(values))}>
        {showGeneral && <Alert tone="error">{errorMessage(create.error)}</Alert>}
        <TextField label="Họ và tên" autoFocus error={errors.fullName?.message} {...form.register('fullName')} />
        <TextField label="Số điện thoại" inputMode="tel" placeholder="0912345678" error={errors.phone?.message} {...form.register('phone')} />
        <TextField label="Tên đăng nhập" autoComplete="off" error={errors.username?.message} {...form.register('username')} />
        <TextField label="Mật khẩu ban đầu" type="password" autoComplete="new-password" error={errors.password?.message} {...form.register('password')} />
      </form>
    </Dialog>
  );
}
