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

const schema = z.object({
  fullName: z.string().trim().min(1, 'Vui lòng nhập họ tên').max(100, 'Họ tên tối đa 100 ký tự'),
  phone: z
    .string()
    .transform((value) => value.replace(/[\s.-]/g, ''))
    .pipe(z.string().regex(/^0[0-9]{9,10}$/, 'Số điện thoại gồm 10–11 chữ số, bắt đầu bằng 0')),
});

type Input = z.input<typeof schema>;
type Output = z.output<typeof schema>;
const FIELDS = ['fullName', 'phone'] as const;

/** Quản lý sửa họ tên, số điện thoại của bảo vệ. Tên đăng nhập không đổi được. Mọi lần sửa được ghi nhật ký. */
export function EditGuardDialog({ guard, onClose, onSaved }: { guard: Guard; onClose: () => void; onSaved: () => void }) {
  const queryClient = useQueryClient();
  const form = useForm<Input, unknown, Output>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: guard.fullName, phone: guard.phone },
  });
  const applyFieldErrors = useFieldErrors(form.setError, FIELDS);

  const save = useMutation({
    mutationFn: (values: Output) => api.updateGuard(guard.id, values),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.guards });
      onSaved();
    },
    onError: applyFieldErrors,
  });

  const { errors } = form.formState;
  const showGeneral = save.isError && !(isApiError(save.error) && save.error.code === 'VALIDATION_ERROR');

  return (
    <Dialog
      title={`Sửa thông tin bảo vệ ${guard.username}`}
      onClose={onClose}
      busy={save.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
            Hủy
          </Button>
          <Button type="submit" form="edit-guard" loading={save.isPending}>
            Lưu
          </Button>
        </>
      }
    >
      <form id="edit-guard" className="space-y-4" noValidate onSubmit={form.handleSubmit((values) => save.mutate(values))}>
        {showGeneral && <Alert tone="error">{errorMessage(save.error)}</Alert>}
        <TextField label="Họ tên" autoFocus error={errors.fullName?.message} {...form.register('fullName')} />
        <TextField label="Số điện thoại" type="tel" error={errors.phone?.message} {...form.register('phone')} />
      </form>
    </Dialog>
  );
}
