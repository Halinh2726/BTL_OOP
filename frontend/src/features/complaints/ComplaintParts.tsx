import { api } from '../../shared/api';
import { Badge } from '../../shared/components/Badge';
import type { CameraReviewStatus, ComplaintStatus, ComplaintView } from '../../shared/types/api';
import {
  CAMERA_STATUS_LABELS, COMPLAINT_STATUS_LABELS, COMPLAINT_TYPE_LABELS, formatDateTime, formatPlate, formatPoints,
} from '../../shared/utils/format';

const TONE: Record<ComplaintStatus, 'blue' | 'amber' | 'green' | 'red' | 'gray'> = {
  MOI: 'blue',
  DANG_XU_LY: 'amber',
  CHO_DUYET_BOI_THUONG: 'amber',
  DA_XU_LY: 'green',
  DA_BOI_THUONG: 'green',
  TU_CHOI: 'red',
  KHONG_BOI_THUONG: 'red',
  DA_HUY: 'gray',
};

const CAMERA_TONE: Record<CameraReviewStatus, 'blue' | 'amber' | 'green' | 'red' | 'gray'> = {
  KHONG_YEU_CAU: 'gray',
  CHO_TRICH_XUAT: 'amber',
  DANG_TRICH_XUAT: 'blue',
  DA_TRICH_XUAT: 'green',
  KHONG_CO_DU_LIEU: 'red',
};

export function ComplaintStatusBadge({ status }: { status: ComplaintStatus }) {
  return <Badge tone={TONE[status]}>{COMPLAINT_STATUS_LABELS[status]}</Badge>;
}

/**
 * Nội dung một khiếu nại — dùng chung cho sinh viên, quản lý, chủ bãi xe. `viewer` quyết định lấy ảnh qua đường dẫn
 * nào (mỗi vai trò có quyền xem ảnh riêng).
 */
export function ComplaintBody({ complaint, viewer }: { complaint: ComplaintView; viewer: 'student' | 'manager' | 'owner' }) {
  const c = complaint;
  return (
    <div className="space-y-1.5 text-sm">
      <p className="font-semibold text-slate-900">
        #{c.id} · {COMPLAINT_TYPE_LABELS[c.type]}
        {viewer !== 'student' && c.studentName && (
          <span className="font-normal text-slate-600"> — {c.studentName} ({c.studentCode})</span>
        )}
      </p>
      <p className="whitespace-pre-line text-slate-700">{c.description}</p>
      <p className="text-xs text-slate-500">Gửi lúc {formatDateTime(c.createdAt)}</p>
      {c.session && (
        <p className="text-xs text-slate-600">
          Lượt gửi <span className="font-mono">{formatPlate(c.session.plateNumber)}</span> vào {formatDateTime(c.session.enteredAt)}
          {c.session.fee !== null && <> · đã thu {formatPoints(c.session.fee)}</>}
        </p>
      )}
      {c.hasPhoto && viewer !== 'owner' && (
        <a href={api.complaintPhotoUrl(c.id, viewer === 'manager')} target="_blank" rel="noreferrer" className="inline-block">
          <img
            src={api.complaintPhotoUrl(c.id, viewer === 'manager')}
            alt={`Ảnh đính kèm khiếu nại #${c.id}`}
            className="mt-1 max-h-32 rounded-lg ring-1 ring-slate-200"
          />
        </a>
      )}
      {(c.cameraRequested || c.cameraStatus !== 'KHONG_YEU_CAU') && (
        <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs font-semibold text-slate-700">Camera:</span>
            <Badge tone={CAMERA_TONE[c.cameraStatus]}>{CAMERA_STATUS_LABELS[c.cameraStatus]}</Badge>
          </div>
          {(c.incidentArea || c.incidentTimeRange) && (
            <p className="text-xs text-slate-600">
              {c.incidentArea && <>Khu vực: <strong>{c.incidentArea}</strong></>}
              {c.incidentArea && c.incidentTimeRange && ' · '}
              {c.incidentTimeRange && <>Khung giờ: <strong>{c.incidentTimeRange}</strong></>}
            </p>
          )}
          {c.cameraNote && (
            <p className="text-xs text-slate-700">
              Ghi chú trích xuất: {c.cameraNote}
              {c.cameraReviewerName && <span className="text-slate-500"> — {c.cameraReviewerName}</span>}
              {c.cameraReviewedAt && <span className="text-slate-500"> ({formatDateTime(c.cameraReviewedAt)})</span>}
            </p>
          )}
          {c.hasCameraPhoto && (
            <a
              href={api.complaintCameraPhotoUrl(c.id, viewer === 'student' ? 'me' : viewer)}
              target="_blank"
              rel="noreferrer"
              className="inline-block"
            >
              <img
                src={api.complaintCameraPhotoUrl(c.id, viewer === 'student' ? 'me' : viewer)}
                alt={`Ảnh trích xuất camera khiếu nại #${c.id}`}
                className="mt-1 max-h-36 rounded-lg ring-1 ring-slate-200"
              />
            </a>
          )}
        </div>
      )}
      {c.proposedPoints !== null && (
        <p className="rounded-lg bg-amber-50 px-3 py-2 text-amber-900">
          Đề xuất bồi thường <strong>{formatPoints(c.proposedPoints)}</strong>
          {c.proposerName && <> bởi {c.proposerName}</>}
          {c.resolution && c.status === 'CHO_DUYET_BOI_THUONG' && <span className="block text-xs">Căn cứ: {c.resolution}</span>}
        </p>
      )}
      {c.resolution && c.status !== 'CHO_DUYET_BOI_THUONG' && (
        <p className={c.status === 'TU_CHOI' ? 'text-red-700' : 'text-emerald-800'}>
          {c.status === 'TU_CHOI' ? 'Lý do từ chối' : 'Kết luận'}: {c.resolution}
          {c.handlerName && <span className="text-xs text-slate-500"> — {c.handlerName}</span>}
        </p>
      )}
      {c.refundTransactionId !== null && (
        <p className="text-emerald-800">Đã hoàn phí (giao dịch điều chỉnh #{c.refundTransactionId})</p>
      )}
      {c.decidedAt && (
        <p className={c.status === 'DA_BOI_THUONG' ? 'text-emerald-800' : 'text-red-700'}>
          {c.status === 'DA_BOI_THUONG' ? 'Chủ bãi đã duyệt bồi thường' : 'Chủ bãi không duyệt bồi thường'}
          {c.decisionNote && <>: {c.decisionNote}</>}
          {c.compensationTransactionId !== null && <span className="text-xs"> (giao dịch #{c.compensationTransactionId})</span>}
        </p>
      )}
    </div>
  );
}
