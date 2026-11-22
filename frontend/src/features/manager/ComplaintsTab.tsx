import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { ConfirmDialog } from '../../shared/components/Dialog';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { CameraReviewStatus, ComplaintStatus, ComplaintView } from '../../shared/types/api';
import { CAMERA_STATUS_LABELS, COMPLAINT_STATUS_LABELS, formatPoints } from '../../shared/utils/format';
import { resizeImage, type PreparedPhoto } from '../../shared/utils/resizeImage';
import { ComplaintBody, ComplaintStatusBadge } from '../complaints/ComplaintParts';
import { parseAmount } from './amount';

type Kind = 'resolve' | 'reject' | 'propose' | 'camera';
type Action = { kind: Kind; complaint: ComplaintView };

const CAMERA_ACTIONS: CameraReviewStatus[] = ['CHO_TRICH_XUAT', 'DANG_TRICH_XUAT', 'DA_TRICH_XUAT', 'KHONG_CO_DU_LIEU'];

const FILTERS: Array<ComplaintStatus | undefined> = [undefined, 'CHO_DUYET_BOI_THUONG', 'DA_XU_LY', 'DA_BOI_THUONG', 'TU_CHOI'];

/** Chỉ thu phí sai có lượt gửi đã thanh toán mới hoàn phí được; chỉ hư hỏng / mất đồ mới đề xuất bồi thường. */
function canRefund(c: ComplaintView): boolean {
  return c.type === 'THU_PHI_SAI' && c.session?.fee != null;
}

function canCompensate(c: ComplaintView): boolean {
  return c.type === 'XE_HU_HONG' || c.type === 'MAT_DO';
}

/**
 * Đợt 4b — quản lý xử lý khiếu nại của sinh viên: tiếp nhận → kết luận (kèm hoàn phí nếu thu sai) / từ chối /
 * đề xuất bồi thường cho chủ bãi xe duyệt.
 */
export function ComplaintsTab() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<ComplaintStatus | undefined>();
  const [action, setAction] = useState<Action | null>(null);
  const [note, setNote] = useState('');
  const [points, setPoints] = useState('');
  const [cameraStatus, setCameraStatus] = useState<CameraReviewStatus>('DANG_TRICH_XUAT');
  const [cameraPhoto, setCameraPhoto] = useState<PreparedPhoto | null>(null);
  const [cameraPhotoError, setCameraPhotoError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const list = useQuery({ queryKey: queryKeys.managerComplaints(status), queryFn: () => api.managerComplaints(status), staleTime: 0 });

  async function refresh() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.managerComplaintsAll }),
      queryClient.invalidateQueries({ queryKey: queryKeys.ledgerAll }),
    ]);
  }

  const start = useMutation({
    mutationFn: (id: number) => api.startComplaint(id),
    onSuccess: async (c) => { await refresh(); setMessage(`Đã tiếp nhận khiếu nại #${c.id}.`); },
  });

  const act = useMutation({
    mutationFn: async ({ kind, complaint }: Action) => {
      if (kind === 'camera') {
        return api.updateComplaintCamera(complaint.id, {
          cameraStatus,
          cameraNote: note.trim() || undefined,
          photoContentType: cameraPhoto?.contentType,
          photoBase64: cameraPhoto?.dataUrl,
        });
      }
      const amount = parseAmount(points) ?? 0;
      if (kind === 'resolve') return api.resolveComplaint(complaint.id, note, canRefund(complaint) ? amount : 0);
      if (kind === 'reject') return api.rejectComplaint(complaint.id, note);
      return api.proposeCompensation(complaint.id, amount, note);
    },
    onSuccess: async (c, vars) => {
      await refresh();
      setAction(null);
      if (vars.kind === 'camera') {
        setMessage(`Đã cập nhật trạng thái trích xuất camera khiếu nại #${c.id}.`);
        return;
      }
      setMessage(
        c.status === 'CHO_DUYET_BOI_THUONG' ? `Đã gửi đề xuất bồi thường #${c.id} cho chủ bãi xe duyệt.`
          : c.refundTransactionId ? `Đã kết luận và hoàn phí cho khiếu nại #${c.id}.` : `Đã cập nhật khiếu nại #${c.id}.`,
      );
    },
  });

  function openAction(kind: Kind, complaint: ComplaintView) {
    act.reset();
    setNote(kind === 'camera' ? (complaint.cameraNote ?? '') : '');
    setPoints(kind === 'resolve' && canRefund(complaint) ? String(complaint.session?.fee ?? '') : '');
    setCameraStatus(complaint.cameraStatus === 'KHONG_YEU_CAU' ? 'DANG_TRICH_XUAT' : complaint.cameraStatus);
    setCameraPhoto(null);
    setCameraPhotoError(null);
    setAction({ kind, complaint });
  }

  async function chooseCameraPhoto(file: File | undefined) {
    setCameraPhotoError(null);
    if (!file) return;
    try {
      setCameraPhoto(await resizeImage(file));
    } catch {
      setCameraPhoto(null);
      setCameraPhotoError('Không đọc được ảnh này. Hãy chọn ảnh JPG/PNG/WEBP khác.');
    }
  }

  const amount = parseAmount(points);
  const valid = action !== null && (
    action.kind === 'camera'
      ? (cameraStatus !== 'KHONG_YEU_CAU')
      : (
          note.trim().length > 0
          && (action.kind !== 'propose' || (amount !== null && amount > 0))
          && (action.kind !== 'resolve' || !canRefund(action.complaint) || points.trim() === '' || amount !== null)
        )
  );

  return (
    <Card title="Khiếu nại của sinh viên">
      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          {FILTERS.map((filter) => (
            <button
              key={filter ?? 'OPEN'}
              type="button"
              onClick={() => { setStatus(filter); setMessage(null); }}
              className={`rounded-full px-3 py-1 text-sm font-semibold ring-1 ring-inset ${
                status === filter ? 'bg-blue-700 text-white ring-blue-700' : 'bg-white text-slate-600 ring-slate-300'
              }`}
            >
              {filter ? COMPLAINT_STATUS_LABELS[filter] : 'Cần xử lý'}
            </button>
          ))}
        </div>
        {message && <Alert tone="success">{message}</Alert>}
        {start.isError && <Alert tone="error">{errorMessage(start.error)}</Alert>}
        {list.isPending && <LoadingBlock />}
        {list.isError && <QueryError error={list.error} onRetry={() => void list.refetch()} />}
        {list.data?.length === 0 && <p className="py-6 text-center text-slate-500">Không có khiếu nại nào.</p>}
        <ul className="divide-y divide-slate-100">
          {list.data?.map((c) => (
            <li key={c.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
              <div className="min-w-0 flex-1"><ComplaintBody complaint={c} viewer="manager" /></div>
              <div className="flex flex-wrap items-center justify-end gap-2">
                <ComplaintStatusBadge status={c.status} />
                {c.status === 'MOI' && (
                  <Button size="sm" loading={start.isPending && start.variables === c.id} onClick={() => start.mutate(c.id)}>
                    Tiếp nhận
                  </Button>
                )}
                {c.status === 'DANG_XU_LY' && (
                  <>
                    <Button size="sm" variant="success" onClick={() => openAction('resolve', c)}>
                      {canRefund(c) ? 'Kết luận / hoàn phí' : 'Kết luận'}
                    </Button>
                    {canCompensate(c) && <Button size="sm" onClick={() => openAction('propose', c)}>Đề xuất bồi thường</Button>}
                  </>
                )}
                {(c.status === 'MOI' || c.status === 'DANG_XU_LY') && (
                  <Button size="sm" variant="secondary" onClick={() => openAction('reject', c)}>Từ chối</Button>
                )}
                {(c.cameraRequested || c.status === 'MOI' || c.status === 'DANG_XU_LY' || c.status === 'CHO_DUYET_BOI_THUONG') && (
                  <Button size="sm" variant="secondary" onClick={() => openAction('camera', c)}>
                    Trích xuất camera
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>

      {action && (
        <ConfirmDialog
          title={
            action.kind === 'camera'
              ? `Trích xuất camera — khiếu nại #${action.complaint.id}`
              : action.kind === 'resolve'
                ? `Kết luận khiếu nại #${action.complaint.id}`
                : action.kind === 'propose'
                  ? `Đề xuất bồi thường — khiếu nại #${action.complaint.id}`
                  : `Từ chối khiếu nại #${action.complaint.id}`
          }
          confirmLabel={
            action.kind === 'camera'
              ? 'Lưu kết quả camera'
              : action.kind === 'resolve'
                ? 'Kết luận'
                : action.kind === 'propose'
                  ? 'Gửi chủ bãi duyệt'
                  : 'Từ chối'
          }
          tone={action.kind === 'reject' ? 'danger' : 'success'}
          pending={act.isPending}
          onCancel={() => setAction(null)}
          onConfirm={() => (valid ? act.mutate(action) : undefined)}
          error={act.isError && <Alert tone="error">{errorMessage(act.error)}</Alert>}
        >
          <div className="space-y-3">
            {action.kind === 'camera' && (
              <>
                <label className="block space-y-1 text-sm font-medium text-slate-700">
                  Trạng thái trích xuất camera
                  <select
                    value={cameraStatus}
                    onChange={(e) => setCameraStatus(e.target.value as CameraReviewStatus)}
                    className="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2"
                  >
                    {CAMERA_ACTIONS.map((st) => (
                      <option key={st} value={st}>{CAMERA_STATUS_LABELS[st]}</option>
                    ))}
                  </select>
                </label>
                <div className="space-y-1">
                  <label htmlFor="camera-evidence-photo" className="block text-sm font-medium text-slate-700">
                    Ảnh cắt từ camera (không bắt buộc)
                  </label>
                  <input
                    id="camera-evidence-photo"
                    type="file"
                    accept="image/*"
                    onChange={(e) => void chooseCameraPhoto(e.target.files?.[0])}
                    className="block w-full text-sm"
                  />
                  {cameraPhotoError && <p className="text-sm text-red-600">{cameraPhotoError}</p>}
                  {cameraPhoto && <img src={cameraPhoto.dataUrl} alt="Ảnh trích xuất camera" className="max-h-36 rounded-lg ring-1 ring-slate-200" />}
                </div>
              </>
            )}
            {(action.kind === 'propose' || (action.kind === 'resolve' && canRefund(action.complaint))) && (
              <label className="block space-y-1 text-sm font-medium text-slate-700">
                {action.kind === 'propose' ? 'Số điểm bồi thường đề xuất' : `Hoàn phí (tối đa ${formatPoints(action.complaint.session?.fee ?? 0)}, để trống nếu không hoàn)`}
                <input
                  inputMode="numeric"
                  value={points}
                  onChange={(e) => setPoints(e.target.value)}
                  className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-right font-mono"
                />
              </label>
            )}
            <label className="block space-y-1 text-sm font-medium text-slate-700">
              {action.kind === 'camera'
                ? 'Ghi chú trích xuất camera'
                : action.kind === 'reject'
                  ? 'Lý do từ chối (bắt buộc)'
                  : action.kind === 'propose'
                    ? 'Căn cứ đề xuất (bắt buộc)'
                    : 'Kết luận gửi sinh viên (bắt buộc)'}
              <textarea rows={3} maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} className="block w-full rounded-lg border border-slate-300 px-3 py-2" />
            </label>
            {action.kind === 'propose' && (
              <p className="text-xs text-slate-500">Điểm bồi thường lấy từ ví chủ bãi xe nên phải được chủ bãi duyệt; bạn không tự duyệt được.</p>
            )}
          </div>
        </ConfirmDialog>
      )}
    </Card>
  );
}
