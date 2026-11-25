import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { ComplaintType } from '../../shared/types/api';
import { COMPLAINT_TYPE_LABELS, formatDateTime, formatPlate, formatPoints } from '../../shared/utils/format';
import { resizeImage, type PreparedPhoto } from '../../shared/utils/resizeImage';
import { ComplaintBody, ComplaintStatusBadge } from '../complaints/ComplaintParts';

const TYPES = Object.keys(COMPLAINT_TYPE_LABELS) as ComplaintType[];

export interface ComplaintPrefill {
  sessionId: number;
  type: ComplaintType;
  nonce: number;
}

/**
 * Đợt 4b & mở rộng — sinh viên khiếu nại thu phí sai, báo hư hỏng / mất đồ,
 * yêu cầu trích xuất camera xem lịch sử hoặc các dịch vụ khác.
 */
export function ComplaintsCard({ prefill }: { prefill?: ComplaintPrefill | null }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [type, setType] = useState<ComplaintType>('THU_PHI_SAI');
  const [sessionId, setSessionId] = useState('');
  const [description, setDescription] = useState('');
  const [cameraRequested, setCameraRequested] = useState(false);
  const [incidentArea, setIncidentArea] = useState('');
  const [incidentTimeRange, setIncidentTimeRange] = useState('');
  const [photo, setPhoto] = useState<PreparedPhoto | null>(null);
  const [photoError, setPhotoError] = useState<string | null>(null);
  const complaints = useQuery({ queryKey: queryKeys.myComplaints, queryFn: api.myComplaints });
  const sessions = useQuery({ queryKey: queryKeys.complaintSessions, queryFn: api.complaintSessions, enabled: open });

  useEffect(() => {
    if (!prefill) return;
    setOpen(true);
    setType(prefill.type);
    setSessionId(String(prefill.sessionId));
    setCameraRequested(prefill.type === 'TRICH_XUAT_CAMERA');
  }, [prefill]);

  async function refresh() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.myComplaints }),
      queryClient.invalidateQueries({ queryKey: queryKeys.myWallet }),
      queryClient.invalidateQueries({ queryKey: queryKeys.myTransactionsAll }),
      queryClient.invalidateQueries({ queryKey: queryKeys.notifications }),
    ]);
  }

  const effectiveCamera = type === 'TRICH_XUAT_CAMERA' || cameraRequested;

  const submit = useMutation({
    mutationFn: () =>
      api.submitComplaint({
        type,
        description,
        sessionId: sessionId ? Number(sessionId) : undefined,
        photoContentType: photo?.contentType,
        photoBase64: photo?.dataUrl,
        cameraRequested: effectiveCamera,
        incidentArea: effectiveCamera && incidentArea.trim() ? incidentArea.trim() : undefined,
        incidentTimeRange: effectiveCamera && incidentTimeRange.trim() ? incidentTimeRange.trim() : undefined,
      }),
    onSuccess: async () => {
      await refresh();
      setOpen(false);
      setDescription('');
      setSessionId('');
      setCameraRequested(false);
      setIncidentArea('');
      setIncidentTimeRange('');
      setPhoto(null);
    },
  });
  const cancel = useMutation({ mutationFn: (id: number) => api.cancelComplaint(id), onSuccess: refresh });

  async function choosePhoto(file: File | undefined) {
    setPhotoError(null);
    if (!file) return;
    try {
      setPhoto(await resizeImage(file));
    } catch {
      setPhoto(null);
      setPhotoError('Không đọc được ảnh này. Hãy chọn ảnh khác.');
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (description.trim()) submit.mutate();
  }

  const descriptionPlaceholder =
    type === 'THU_PHI_SAI'
      ? 'Ví dụ: tôi ra lúc 17:40 nhưng bị trừ 5.000 điểm'
      : type === 'TRICH_XUAT_CAMERA'
        ? 'Ví dụ: xin trích xuất camera cổng A2 khoảng 16:30–17:00 để kiểm tra ai chạm vào xe'
        : type === 'DICH_VU_KHAC'
          ? 'Ví dụ: nhờ hỗ trợ bơm lốp / tìm thẻ xe / kiểm tra vé tháng hoặc dịch vụ tại bãi'
          : 'Ví dụ: xe bị đổ, vỡ gương bên trái, phát hiện lúc 17:05';

  return (
    <Card
      title="Khiếu nại, trích xuất camera & dịch vụ khác"
      actions={!open && <Button size="sm" onClick={() => { submit.reset(); setOpen(true); }}>Gửi yêu cầu / khiếu nại</Button>}
    >
      <div className="space-y-4">
        {open && (
          <form onSubmit={onSubmit} className="space-y-3 rounded-xl bg-slate-50 p-4" noValidate>
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="block space-y-1 text-sm font-medium text-slate-700">
                Loại yêu cầu
                <select value={type} onChange={(e) => setType(e.target.value as ComplaintType)} className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2">
                  {TYPES.map((t) => <option key={t} value={t}>{COMPLAINT_TYPE_LABELS[t]}</option>)}
                </select>
              </label>
              <label className="block space-y-1 text-sm font-medium text-slate-700">
                Lượt gửi liên quan {type === 'THU_PHI_SAI' ? '(nên chọn để được hoàn phí)' : '(nếu có)'}
                <select value={sessionId} onChange={(e) => setSessionId(e.target.value)} className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2">
                  <option value="">— Không chọn —</option>
                  {sessions.data?.map((s) => (
                    <option key={s.id} value={s.id}>
                      {formatPlate(s.plateNumber)} · vào {formatDateTime(s.enteredAt)}{s.fee !== null ? ` · ${formatPoints(s.fee)}` : ' · đang gửi'}
                    </option>
                  ))}
                </select>
              </label>
            </div>

            <div className="rounded-lg border border-slate-200 bg-white p-3 space-y-2.5">
              <label className="flex items-center gap-2 text-sm font-medium text-slate-800">
                <input
                  type="checkbox"
                  checked={effectiveCamera}
                  disabled={type === 'TRICH_XUAT_CAMERA'}
                  onChange={(e) => setCameraRequested(e.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-blue-600"
                />
                Yêu cầu Quản lý trích xuất camera để đối chiếu sự việc
              </label>
              {effectiveCamera && (
                <div className="grid gap-3 sm:grid-cols-2">
                  <label className="block space-y-1 text-xs font-medium text-slate-700">
                    Khu vực bãi xe / cổng
                    <input
                      type="text"
                      maxLength={150}
                      value={incidentArea}
                      onChange={(e) => setIncidentArea(e.target.value)}
                      placeholder="Ví dụ: Khu B dãy xe máy / Cổng chính"
                      className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-normal"
                    />
                  </label>
                  <label className="block space-y-1 text-xs font-medium text-slate-700">
                    Khung giờ nghi vấn
                    <input
                      type="text"
                      maxLength={150}
                      value={incidentTimeRange}
                      onChange={(e) => setIncidentTimeRange(e.target.value)}
                      placeholder="Ví dụ: 16:30 – 17:15 ngày hôm nay"
                      className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm font-normal"
                    />
                  </label>
                </div>
              )}
            </div>

            <label className="block space-y-1 text-sm font-medium text-slate-700">
              Mô tả chi tiết
              <textarea
                rows={3}
                maxLength={1000}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder={descriptionPlaceholder}
                className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2"
              />
            </label>
            <div className="space-y-1">
              <label htmlFor="complaint-photo" className="block text-sm font-medium text-slate-700">Ảnh chụp (không bắt buộc)</label>
              <input id="complaint-photo" type="file" accept="image/*" onChange={(e) => void choosePhoto(e.target.files?.[0])} className="block w-full text-sm" />
              {photoError && <p className="text-sm text-red-600">{photoError}</p>}
              {photo && <img src={photo.dataUrl} alt="Ảnh sẽ gửi" className="max-h-40 rounded-lg ring-1 ring-slate-200" />}
            </div>
            {submit.isError && <Alert tone="error">{errorMessage(submit.error)}</Alert>}
            <div className="flex gap-2">
              <Button type="submit" loading={submit.isPending} disabled={!description.trim()}>Gửi cho quản lý</Button>
              <Button variant="secondary" onClick={() => setOpen(false)} disabled={submit.isPending}>Đóng</Button>
            </div>
          </form>
        )}

        {cancel.isError && <Alert tone="error">{errorMessage(cancel.error)}</Alert>}
        {complaints.isPending && <LoadingBlock />}
        {complaints.isError && <QueryError error={complaints.error} onRetry={() => void complaints.refetch()} />}
        {complaints.data?.length === 0 && !open && <p className="text-sm text-slate-500">Bạn chưa gửi khiếu nại nào.</p>}
        <ul className="divide-y divide-slate-100">
          {complaints.data?.map((c) => (
            <li key={c.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
              <div className="min-w-0 flex-1"><ComplaintBody complaint={c} viewer="student" /></div>
              <div className="flex items-center gap-2">
                <ComplaintStatusBadge status={c.status} />
                {c.status === 'MOI' && (
                  <Button size="sm" variant="secondary" loading={cancel.isPending && cancel.variables === c.id} onClick={() => cancel.mutate(c.id)}>
                    Hủy
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  );
}
