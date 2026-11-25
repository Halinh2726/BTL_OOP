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
import { ELECTRIC_MOTORCYCLE_PLATE, MOTORCYCLE_PLATE, formatPlate, isPlateValidForVehicleType, normalizePlate } from '../../shared/utils/format';

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
    } else if (!isPlateValidForVehicleType(values.plateNumber, values.vehicleTypeCode)) {
      ctx.addIssue({
        code: 'custom',
        path: ['plateNumber'],
        message: values.vehicleTypeCode === 'XE_MAY_DIEN'
          ? 'Xe máy điện phải có biển số theo dạng mã vùng + MĐ + 5 chữ số (ví dụ 29MĐ-123.45)'
          : 'Xe máy phải có biển số theo dạng sê-ri chữ/số + 5 chữ số (ví dụ 36S4-12345 hoặc 26DC-12345)',
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
  const selectedType = form.watch('vehicleTypeCode');
  const isBicycle = selectedType === BICYCLE_TYPE;
  const isElectric = selectedType === 'XE_MAY_DIEN';
  const electricParts = /^([0-9]{0,2})(?:MĐ([0-9]{0,5}))?$/.exec(typed);
  const electricRegion = electricParts?.[1] ?? '';
  const electricSerial = electricParts?.[2] ?? '';

  function updateElectricPlate(regionInput: string, serialInput: string) {
    const region = regionInput.replace(/\D/g, '').slice(0, 2);
    const serial = serialInput.replace(/\D/g, '').slice(0, 5);
    form.setValue('plateNumber', region.length === 2 ? `${region}MĐ${serial}` : region, {
      shouldDirty: true,
      shouldValidate: region.length === 2 && serial.length === 5,
    });
    if (region.length !== 2 || serial.length !== 5) form.clearErrors('plateNumber');
  }

  const register = useMutation({
    mutationFn: api.registerVehicle,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.myVehicles });
      onClose();
    },
    onError: (error) => {
      applyFieldErrors(error);
      if (isApiError(error) && ['PLATE_EXISTS', 'PLATE_REQUIRED', 'PLATE_TYPE_MISMATCH'].includes(error.code)) {
        form.setError('plateNumber', { type: 'server', message: error.message });
      }
    },
  });

  const { errors } = form.formState;
  const showGeneral =
    register.isError &&
    !(isApiError(register.error) && ['VALIDATION_ERROR', 'PLATE_EXISTS', 'PLATE_REQUIRED', 'PLATE_TYPE_MISMATCH'].includes(register.error.code));

  const typedPattern = form.watch('vehicleTypeCode') === 'XE_MAY_DIEN'
    ? ELECTRIC_MOTORCYCLE_PLATE
    : MOTORCYCLE_PLATE;
  const hint = typed && typedPattern.test(typed)
    ? `Sẽ lưu là ${formatPlate(typed)}`
    : isElectric
      ? 'Nhập 2 số mã vùng và 5 số thứ tự.'
      : 'Nhập biển số đúng cấu trúc của loại xe đã chọn';

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
        <SelectField
          label="Loại xe"
          error={errors.vehicleTypeCode?.message}
          {...form.register('vehicleTypeCode')}
          onChange={(event) => {
            const nextType = event.target.value;
            form.setValue('vehicleTypeCode', nextType, { shouldValidate: true });
            if (nextType !== selectedType) form.setValue('plateNumber', '', { shouldValidate: true });
          }}
        >
          {types.map((type) => (
            <option key={type.code} value={type.code}>
              {type.name}
            </option>
          ))}
        </SelectField>
        {isBicycle ? (
          <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
            Xe đạp không có biển số. Bãi sẽ tự cấp mã XD khi đăng ký.
          </p>
        ) : isElectric ? (
          <div className="space-y-1">
            <label className="block text-sm font-medium text-slate-700">Biển số xe máy điện</label>
            <div className="flex items-center gap-2">
              <input
                aria-label="Mã vùng gồm 2 chữ số"
                autoFocus
                inputMode="numeric"
                maxLength={2}
                placeholder="29"
                value={electricRegion}
                onChange={(event) => updateElectricPlate(event.target.value, electricSerial)}
                className="block w-16 rounded-lg border border-slate-300 bg-white px-3 py-2 text-center font-mono text-slate-900 shadow-sm focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-200"
              />
              <span className="rounded-lg border border-slate-200 bg-slate-100 px-3 py-2 font-mono font-semibold text-slate-700">MĐ</span>
              <input
                aria-label="5 chữ số thứ tự"
                inputMode="numeric"
                maxLength={5}
                disabled={electricRegion.length !== 2}
                placeholder="12345"
                value={electricSerial}
                onChange={(event) => updateElectricPlate(electricRegion, event.target.value)}
                className="block w-32 rounded-lg border border-slate-300 bg-white px-3 py-2 font-mono text-slate-900 shadow-sm focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-200"
              />
            </div>
            {errors.plateNumber?.message ? (
              <p className="text-sm text-red-600">{errors.plateNumber.message}</p>
            ) : (
              <p className="text-sm text-slate-500">{hint} Ví dụ: 29MĐ-123.45</p>
            )}
          </div>
        ) : (
          <TextField
            label="Biển số"
            placeholder="36S4-12345 hoặc 26DC-12345"
            autoFocus
            className="font-mono uppercase"
            hint={hint}
            error={errors.plateNumber?.message}
            {...form.register('plateNumber')}
          />
        )}
      </form>
    </Dialog>
  );
}
