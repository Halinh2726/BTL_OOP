import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api, errorMessage, isApiError } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { SelectField, TextField } from '../../shared/components/Field';
import { useFieldErrors } from '../../shared/hooks/useFieldErrors';
import type { VehicleType } from '../../shared/types/api';
import { normalizePlate } from '../../shared/utils/format';

const schema = z.object({
  plateNumber: z
    .string()
    .min(1, 'Vui lòng nhập biển số')
    .transform(normalizePlate)
    .pipe(z.string().regex(/^[A-Z0-9]{5,20}$/, 'Biển số 5–20 ký tự, chỉ gồm chữ và số')),
  vehicleTypeCode: z.string().min(1, 'Vui lòng chọn loại xe'),
});

type Input = z.input<typeof schema>;
type Output = z.output<typeof schema>;
const FIELDS = ['plateNumber', 'vehicleTypeCode'] as const;

export function RegisterVehicleDialog({ types, onClose }: { types: VehicleType[]; onClose: () => void }) {
  const queryClient = useQueryClient();
  const form = useForm<Input, unknown, Output>({
    resolver: zodResolver(schema),
    defaultValues: { plateNumber: '', vehicleTypeCode: types[0]?.code ?? '' },
  });
  const applyFieldErrors = useFieldErrors(form.setError, FIELDS);

  const register = useMutation({
    mutationFn: api.registerVehicle,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.myVehicles });
      onClose();
    },
    onError: (error) => {
      applyFieldErrors(error);
      if (isApiError(error) && error.code === 'PLATE_EXISTS') {
        form.setError('plateNumber', { type: 'server', message: error.message });
      }
    },
  });

  const { errors } = form.formState;
  const showGeneral =
    register.isError && !(isApiError(register.error) && ['VALIDATION_ERROR', 'PLATE_EXISTS'].includes(register.error.code));

  return (
    <Dialog
      title="Đăng ký xe"
      onClose={onClose}
      busy={register.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={register.isPending}>
            Hủy
          </Button>
          <Button type="submit" form="register-vehicle" loading={register.isPending}>
            Đăng ký
          </Button>
        </>
      }
    >
      <form id="register-vehicle" className="space-y-4" noValidate onSubmit={form.handleSubmit((values) => register.mutate(values))}>
        {showGeneral && <Alert tone="error">{errorMessage(register.error)}</Alert>}
        <TextField
          label="Biển số"
          placeholder="29A-123.45"
          autoFocus
          className="font-mono uppercase"
          hint="Có thể gõ kèm dấu chấm, gạch ngang — hệ thống tự bỏ đi"
          error={errors.plateNumber?.message}
          {...form.register('plateNumber')}
        />
        <SelectField label="Loại xe" error={errors.vehicleTypeCode?.message} {...form.register('vehicleTypeCode')}>
          {types.map((type) => (
            <option key={type.code} value={type.code}>
              {type.name}
            </option>
          ))}
        </SelectField>
      </form>
    </Dialog>
  );
}
