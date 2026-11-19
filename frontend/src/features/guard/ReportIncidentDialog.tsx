import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api, errorMessage, isApiError } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { SelectField, TextField } from '../../shared/components/Field';
import { useFieldErrors } from '../../shared/hooks/useFieldErrors';
import type { IncidentItem, IncidentType } from '../../shared/types/api';
import { INCIDENT_TYPE_LABELS, normalizePlate } from '../../shared/utils/format';
import { resizeImage, type PreparedPhoto } from './resizeImage';

const TYPES = Object.keys(INCIDENT_TYPE_LABELS) as IncidentType[];

const schema = z.object({
  type: z.enum(['XE_GUI_QUA_LAU', 'XE_CHUA_DANG_KY', 'XE_HU_HONG', 'KHAC']),
  plateNumber: z
    .string()
    .transform(normalizePlate)
    .refine((value) => value === '' || /^[A-Z0-9]{5,20}$/.test(value), 'Biển số 5–20 ký tự, chỉ gồm chữ và số'),
  description: z.string().trim().min(1, 'Vui lòng mô tả sự cố').max(1000, 'Mô tả tối đa 1000 ký tự'),
});

type Input = z.input<typeof schema>;
type Output = z.output<typeof schema>;
const FIELDS = ['type', 'plateNumber', 'description'] as const;

interface ReportIncidentDialogProps {
  /** Mở từ danh sách xe trong bãi: điền sẵn loại "xe gửi quá lâu" và biển số. */
  preset?: { type: IncidentType; plateNumber: string };
  onClose: () => void;
  onReported: (incident: IncidentItem) => void;
}

/** Báo sự cố cho quản lý: chọn loại, biển số (nếu có), mô tả, chụp ảnh bằng camera sau của máy tính bảng. */
export function ReportIncidentDialog({ preset, onClose, onReported }: ReportIncidentDialogProps) {
  const queryClient = useQueryClient();
  const [photo, setPhoto] = useState<PreparedPhoto | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const form = useForm<Input, unknown, Output>({
    resolver: zodResolver(schema),
    defaultValues: { type: preset?.type ?? 'XE_HU_HONG', plateNumber: preset?.plateNumber ?? '', description: '' },
  });
  const applyFieldErrors = useFieldErrors(form.setError, FIELDS);

  const report = useMutation({
    mutationFn: (values: Output) =>
      api.reportIncident({
        ...values,
        photoContentType: photo?.contentType,
        photoBase64: photo?.dataUrl,
      }),
    onSuccess: async (incident) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.myIncidents }),
        queryClient.invalidateQueries({ queryKey: queryKeys.currentShift }),
      ]);
      onReported(incident);
    },
    onError: applyFieldErrors,
  });

  async function choosePhoto(file: File | undefined) {
    setPhotoError(null);
    if (!file) return;
    try {
      setPhoto(await resizeImage(file));
    } catch {
      setPhoto(null);
      setPhotoError('Không đọc được ảnh này. Hãy chụp lại.');
    }
  }

  const { errors } = form.formState;
  const showGeneral = report.isError && !(isApiError(report.error) && report.error.code === 'VALIDATION_ERROR');

  return (
    <Dialog
      title="Báo sự cố"
      onClose={onClose}
      busy={report.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={report.isPending}>
            Hủy
          </Button>
          <Button type="submit" form="report-incident" variant="danger" loading={report.isPending}>
            Gửi cho quản lý
          </Button>
        </>
      }
    >
      <form id="report-incident" className="space-y-4" noValidate onSubmit={form.handleSubmit((values) => report.mutate(values))}>
        {showGeneral && <Alert tone="error">{errorMessage(report.error)}</Alert>}
        <SelectField label="Loại sự cố" error={errors.type?.message} {...form.register('type')}>
          {TYPES.map((type) => (
            <option key={type} value={type}>
              {INCIDENT_TYPE_LABELS[type]}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Biển số (nếu có)"
          className="font-mono uppercase"
          placeholder="29A-123.45"
          error={errors.plateNumber?.message}
          {...form.register('plateNumber')}
        />
        <div className="space-y-1">
          <label htmlFor="incident-description" className="block text-sm font-medium text-slate-700">
            Mô tả
          </label>
          <textarea
            id="incident-description"
            rows={3}
            className="block w-full rounded-lg border border-slate-300 px-3 py-2 shadow-sm focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-200"
            placeholder="Ví dụ: xe bị vỡ gương chiếu hậu bên trái, phát hiện lúc 14:20"
            {...form.register('description')}
          />
          {errors.description?.message && <p className="text-sm text-red-600">{errors.description.message}</p>}
        </div>
        <div className="space-y-2">
          <label htmlFor="incident-photo" className="block text-sm font-medium text-slate-700">
            Ảnh chụp (không bắt buộc)
          </label>
          <input
            id="incident-photo"
            type="file"
            accept="image/*"
            capture="environment"
            onChange={(event) => void choosePhoto(event.target.files?.[0])}
            className="block w-full text-sm"
          />
          {photoError && <p className="text-sm text-red-600">{photoError}</p>}
          {photo && <img src={photo.dataUrl} alt="Ảnh sự cố sẽ gửi" className="max-h-48 rounded-lg ring-1 ring-slate-200" />}
        </div>
      </form>
    </Dialog>
  );
}
