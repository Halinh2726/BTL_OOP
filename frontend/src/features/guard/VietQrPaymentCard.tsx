import { useState } from 'react';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import type { VietQrOrder } from '../../shared/types/api';
import { formatNumber } from '../../shared/utils/format';

interface VietQrPaymentCardProps {
  order: VietQrOrder;
  onConfirm: () => void;
  confirming: boolean;
  onCancel: () => void;
  error?: string | null;
}

/**
 * Thẻ hiển thị mã VietQR động khi ví sinh viên không đủ điểm.
 * Cho phép sinh viên quét mã bằng bất kỳ app ngân hàng nào (Napas247) và bảo vệ xác nhận cho xe ra.
 */
export function VietQrPaymentCard({ order, onConfirm, confirming, onCancel, error }: VietQrPaymentCardProps) {
  const [copiedField, setCopiedField] = useState<string | null>(null);

  function copy(text: string, label: string) {
    if (navigator.clipboard) {
      void navigator.clipboard.writeText(text);
      setCopiedField(label);
      setTimeout(() => setCopiedField(null), 2000);
    }
  }

  return (
    <Card className="border-2 border-emerald-500 bg-gradient-to-b from-emerald-50/50 to-white">
      <div className="space-y-4">
        {/* Banner cảnh báo thiếu điểm & hướng dẫn chuyển sang VietQR */}
        <div className="flex items-center justify-between rounded-lg bg-amber-50 p-3 text-amber-800 ring-1 ring-amber-200">
          <div className="flex items-center gap-2">
            <span className="text-xl">⚠️</span>
            <div>
              <p className="font-bold">Ví sinh viên không đủ điểm</p>
              <p className="text-sm text-amber-700">Chuyển sang thanh toán trực tiếp tại cổng qua VietQR Napas247.</p>
            </div>
          </div>
          <span className="rounded bg-amber-200 px-2 py-1 font-mono text-xs font-bold text-amber-900">
            Đơn #{order.sessionId}
          </span>
        </div>

        {/* Khối quét mã QR và thông tin chuyển khoản */}
        <div className="flex flex-col items-center gap-6 md:flex-row md:items-start">
          {/* Ảnh mã VietQR */}
          <div className="flex flex-col items-center gap-2">
            <div className="relative flex h-64 w-64 items-center justify-center overflow-hidden rounded-2xl bg-white p-2 shadow-md ring-2 ring-emerald-500/30">
              <img
                src={order.qrCodeUrl}
                alt={`Mã VietQR thanh toán lượt gửi #${order.sessionId}`}
                className="h-full w-full object-contain"
                loading="eager"
              />
            </div>
            <p className="text-xs font-semibold text-emerald-700">Mã VietQR Napas247 tự động</p>
          </div>

          {/* Chi tiết tài khoản nhận tiền */}
          <div className="flex-1 space-y-3 w-full">
            <dl className="space-y-1 text-base">
              <InfoRow label="Biển số xe" value={<span className="font-mono font-bold">{order.plateNumber}</span>} />
              <InfoRow label="Chủ xe" value={`${order.fullName} (${order.studentCode})`} />
              <InfoRow
                label="Số tiền cần trả"
                value={<span className="text-2xl font-extrabold text-red-600">{formatNumber(order.fee)} đ</span>}
              />
              <InfoRow
                label="Ngân hàng"
                value={<span className="font-semibold text-slate-800">{order.bankId}</span>}
              />
              <InfoRow
                label="Số tài khoản"
                value={
                  <div className="flex items-center gap-2">
                    <span className="font-mono font-bold text-slate-900">{order.accountNo}</span>
                    <button
                      type="button"
                      onClick={() => copy(order.accountNo, 'acc')}
                      className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600 hover:bg-slate-200"
                    >
                      {copiedField === 'acc' ? 'Đã chép!' : 'Chép'}
                    </button>
                  </div>
                }
              />
              <InfoRow label="Người nhận" value={<span className="font-medium">{order.accountName}</span>} />
              <InfoRow
                label="Nội dung CK"
                value={
                  <div className="flex items-center gap-2">
                    <span className="rounded bg-amber-100 px-2 py-0.5 font-mono font-bold text-amber-900">
                      {order.transferContent}
                    </span>
                    <button
                      type="button"
                      onClick={() => copy(order.transferContent, 'content')}
                      className="rounded bg-slate-100 px-2 py-0.5 text-xs text-slate-600 hover:bg-slate-200"
                    >
                      {copiedField === 'content' ? 'Đã chép!' : 'Chép'}
                    </button>
                  </div>
                }
              />
            </dl>

            <p className="text-xs text-slate-500">
              * Sinh viên quét mã QR bằng app ngân hàng bất kỳ để chuyển tiền. Sau khi hoàn tất chuyển khoản, bảo vệ bấm nút
              xác nhận bên dưới để mở cổng cho xe ra.
            </p>
          </div>
        </div>

        {error && <Alert tone="error">{error}</Alert>}

        {/* Nút hành động cho bảo vệ */}
        <div className="flex flex-col gap-3 pt-2 sm:flex-row">
          <Button variant="secondary" size="xl" className="sm:w-1/3" onClick={onCancel} disabled={confirming}>
            Hủy / Quay lại
          </Button>
          <Button
            variant="success"
            size="xl"
            className="flex-1 font-bold shadow-md"
            loading={confirming}
            onClick={onConfirm}
          >
            <svg viewBox="0 0 24 24" className="mr-2 h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2.5">
              <polyline points="20 6 9 17 4 12" />
            </svg>
            Xác nhận đã nhận chuyển khoản VietQR
          </Button>
        </div>
      </div>
    </Card>
  );
}

