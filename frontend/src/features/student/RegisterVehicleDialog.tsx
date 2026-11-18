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
import { STANDARD_PLATE, formatPlate, normalizePlate } from '../../shared/utils/format';

/** Xe đạp không có biển được để trống: bãi cấp mã XD-xxxxx (cùng RegisterVehicleUseCase.BICYCLE_TYPE). */
const BICYCLE_TYPE = 'XE_DAP';

const schema = z
  .object({
    plateNumber: z.string().transform(normalizePlate),
    vehicleTypeCode: z.string().min(1, 'Vui lòng chọn loại xe'),
  })
  .superRefine((values, ctx) => {
    if (values.plateNumber === '') {
      if (values.vehicleTypeCode !== BICYCLE_TYPE) {
        ctx.addIssue({ code: 'custom', path: ['plateNumber'], message: 'Vui lòng nhập biển số' });
      }
    } else if (!STANDARD_PLATE.test(values.plateNumber)) {
      ctx.addIssue({
        code: 'custom',
        path: ['plateNumber'],
        message: 'Biển số theo dạng 29A-1514 hoặc 29A-123.45 (tối đa 5 số cuối)',
      });
    }
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
  const typed = normalizePlate(form.watch('plateNumber') ?? '');
  const isBicycle = form.watch('vehicleTypeCode') === BICYCLE_TYPE;

  const register = useMutation({
    mutationFn: api.registerVehicle,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.myVehicles });
      onClose();
    },
    onError: (error) => {
      applyFieldErrors(error);
      if (isApiError(error) && ['PLATE_EXISTS', 'PLATE_REQUIRED'].includes(error.code)) {
        form.setError('plateNumber', { type: 'server', message: error.message });
      }
    },
  });

  const { errors } = form.formState;
  const showGeneral =
    register.isError &&
    !(isApiError(register.error) && ['VALIDATION_ERROR', 'PLATE_EXISTS', 'PLATE_REQUIRED'].includes(register.error.code));

  const hint =
    typed && STANDARD_PLATE.test(typed)
      ? `Sẽ lưu là ${formatPlate(typed)}`
      : isBicycle
        ? 'Xe đạp không có biển: để trống, bãi sẽ cấp mã dạng XD-00001'
        : 'Gõ có hoặc không có dấu chấm, gạch ngang đều được';

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
        <SelectField label="Loại xe" error={errors.vehicleTypeCode?.message} {...form.register('vehicleTypeCode')}>
          {types.map((type) => (
            <option key={type.code} value={type.code}>
              {type.name}
            </option>
          ))}
        </SelectField>
        <TextField
          label={isBicycle ? 'Biển số (không bắt buộc với xe đạp)' : 'Biển số'}
          placeholder="29A-123.45"
          autoFocus
          className="font-mono uppercase"
          hint={hint}
          error={errors.plateNumber?.message}
          {...form.register('plateNumber')}
        />
      </form>
    </Dialog>
  );
}
