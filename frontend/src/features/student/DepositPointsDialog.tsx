import { useQuery, useQueryClient } from '@tanstack/react-query';
import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { Spinner } from '../../shared/components/Spinner';
import { formatNumber } from '../../shared/utils/format';
import { playSuccessSound } from '../../shared/utils/sound';
import { buildVietQrPayload } from '../../shared/utils/vietQr';
import { useCurrentUser } from '../auth/session';

interface DepositPointsDialogProps {
  onClose: () => void;
  currentBalance?: number;
}

const PRESET_AMOUNTS = [20000, 50000, 100000, 200000, 500000];
const DEFAULT_BANK_ID = 'MB';
const DEFAULT_ACCOUNT_NO = '0987654321';
const DEFAULT_ACCOUNT_NAME = 'BAI XE HOC VIEN PTIT';

export function DepositPointsDialog({ onClose, currentBalance }: DepositPointsDialogProps) {
  const queryClient = useQueryClient();
  const currentUser = useCurrentUser();

  const [amount, setAmount] = useState<number>(50000);
  const [customAmount, setCustomAmount] = useState<string>('');
  const [copiedField, setCopiedField] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [checkMessage, setCheckMessage] = useState<string | null>(null);

  // QR Code dạng base64 tạo cục bộ trên client (0ms delay, 100% offline)
  const [localQrDataUrl, setLocalQrDataUrl] = useState<string | null>(null);
  const [useLocalQr, setUseLocalQr] = useState<boolean>(false);

  // Lấy thông tin từ backend (nếu có)
  const depositQuery = useQuery({
    queryKey: queryKeys.depositInfo(amount),
    queryFn: () => api.getDepositInfo(amount),
    staleTime: 60000,
    retry: 1,
  });

  // Xác định mã sinh viên: ưu tiên kết quả từ API, fallback sang username hiện tại
  const studentCode =
    depositQuery.data?.studentCode ||
    (currentUser.user.username ? currentUser.user.username.toUpperCase() : 'B21DCCN001');

  const bankId = depositQuery.data?.bankId || DEFAULT_BANK_ID;
  const accountNo = depositQuery.data?.accountNo || DEFAULT_ACCOUNT_NO;
  const accountName = depositQuery.data?.accountName || DEFAULT_ACCOUNT_NAME;
  const transferContent = `NAP ${studentCode}`;

  // Link ảnh VietQR Napas247 từ dịch vụ công cộng
  const vietQrImageUrl = `https://img.vietqr.io/image/${bankId}-${accountNo}-compact2.png?amount=${amount}&addInfo=${encodeURIComponent(
    transferContent,
  )}&accountName=${encodeURIComponent(accountName)}`;

  // Tự động tạo mã QR Napas247 EMVCo cục bộ bằng thư viện qrcode mỗi khi amount hoặc studentCode thay đổi
  useEffect(() => {
    const emvcoPayload = buildVietQrPayload({
      bankId,
      accountNo,
      amount,
      message: transferContent,
    });

    QRCode.toDataURL(emvcoPayload, {
      width: 240,
      margin: 1,
      color: { dark: '#0f172a', light: '#ffffff' },
    })
      .then((url) => {
        setLocalQrDataUrl(url);
      })
      .catch(() => {
        // Nếu lỗi tạo QR nội bộ thì giữ nguyên
      });
  }, [bankId, accountNo, amount, transferContent]);

  const handleCopy = async (text: string, fieldName: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedField(fieldName);
      setTimeout(() => setCopiedField(null), 2000);
    } catch {
      // Bỏ qua lỗi sao chép
    }
  };

  const handleSelectPreset = (value: number) => {
    setAmount(value);
    setCustomAmount('');
    setCheckMessage(null);
  };

  const handleCustomChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/\D/g, '');
    setCustomAmount(raw);
    const parsed = Number(raw);
    if (parsed > 0) {
      setAmount(parsed);
      setCheckMessage(null);
    }
  };

  const handleCheckBalance = async () => {
    setChecking(true);
    setCheckMessage(null);
    try {
      const prev = currentBalance;
      const updated = await queryClient.fetchQuery({
        queryKey: queryKeys.myWallet,
        queryFn: () => api.myWallet(),
      });
      void queryClient.invalidateQueries({ queryKey: queryKeys.myTransactionsAll });

      if (prev !== undefined && updated.balance > prev) {
        playSuccessSound();
        setCheckMessage(
          `Đã nạp thành công! Số dư mới: ${formatNumber(updated.balance)} điểm (+${formatNumber(
            updated.balance - prev,
          )})`,
        );
      } else {
        setCheckMessage(
          `Số dư hiện tại: ${formatNumber(
            updated.balance,
          )} điểm (Chưa ghi nhận tiền vào ví. Nếu bạn vừa chuyển khoản, vui lòng đợi 2-5 giây)`,
        );
      }
    } catch {
      setCheckMessage('Không thể kết nối máy chủ để kiểm tra số dư');
    } finally {
      setChecking(false);
    }
  };

  return (
    <Dialog
      title="Nạp điểm ví bằng VietQR (Napas247)"
      onClose={onClose}
      footer={
        <div className="flex w-full items-center justify-between gap-2">
          <Button size="sm" variant="secondary" onClick={handleCheckBalance} disabled={checking}>
            {checking ? 'Đang kiểm tra...' : '🔄 Kiểm tra số dư'}
          </Button>
          <Button size="sm" onClick={onClose}>
            Đóng
          </Button>
        </div>
      }
    >
      <div className="space-y-4 text-sm text-slate-700">
        {/* Chọn số tiền nạp */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500">
            Chọn số tiền muốn nạp
          </label>
          <div className="mt-1.5 grid grid-cols-3 gap-2 sm:grid-cols-5">
            {PRESET_AMOUNTS.map((val) => (
              <button
                key={val}
                type="button"
                onClick={() => handleSelectPreset(val)}
                className={`rounded-lg border px-2 py-1.5 text-center text-xs font-semibold transition ${
                  amount === val && !customAmount
                    ? 'border-blue-600 bg-blue-50 text-blue-700 shadow-sm'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                }`}
              >
                {formatNumber(val)} đ
              </button>
            ))}
          </div>

          <div className="mt-2 flex items-center gap-2">
            <span className="text-xs text-slate-500">Số tiền khác:</span>
            <input
              type="text"
              inputMode="numeric"
              placeholder="Nhập số tiền..."
              value={customAmount}
              onChange={handleCustomChange}
              className="flex-1 rounded-md border border-slate-300 px-2.5 py-1 text-xs focus:border-blue-500 focus:outline-none"
            />
            <span className="text-xs font-medium text-slate-500">VNĐ</span>
          </div>
        </div>

        {/* Mã VietQR động */}
        <div className="flex flex-col items-center rounded-xl border border-blue-100 bg-blue-50/40 p-4">
          <div className="relative flex h-56 w-56 items-center justify-center rounded-lg border border-slate-200 bg-white p-2 shadow-sm">
            {/* Ưu tiên hiển thị ảnh VietQR; nếu lỗi hoặc người dùng chọn thì hiển thị QR local EMVCo */}
            {!useLocalQr ? (
              <img
                src={vietQrImageUrl}
                alt="VietQR Napas247 Nạp Tiền"
                className="h-full w-full object-contain"
                onError={() => setUseLocalQr(true)}
              />
            ) : localQrDataUrl ? (
              <div className="flex flex-col items-center">
                <img
                  src={localQrDataUrl}
                  alt="Mã QR Napas247 cục bộ"
                  className="h-44 w-44 object-contain"
                />
                <span className="text-[10px] font-medium text-slate-500">Mã QR Napas247 tự động</span>
              </div>
            ) : (
              <Spinner className="h-8 w-8 text-blue-600" />
            )}
          </div>

          <div className="mt-2 flex items-center gap-2">
            <p className="text-xs font-medium text-slate-600">
              Quét bằng app ngân hàng (MB, Vietcombank, Momo, VNPay...)
            </p>
            {localQrDataUrl && (
              <button
                type="button"
                onClick={() => setUseLocalQr((prev) => !prev)}
                className="text-[11px] text-blue-600 hover:underline"
              >
                {useLocalQr ? 'Hiển thị mẫu thẻ' : 'Đổi chuẩn QR'}
              </button>
            )}
          </div>
        </div>

        {/* Thông tin chuyển khoản chi tiết */}
        <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50/60 p-3 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-slate-500">Ngân hàng:</span>
            <span className="font-semibold text-slate-900">{bankId} (MB Bank)</span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-500">Số tài khoản:</span>
            <div className="flex items-center gap-1.5">
              <span className="font-mono text-sm font-bold text-blue-700">{accountNo}</span>
              <button
                type="button"
                onClick={() => handleCopy(accountNo, 'acc')}
                className="rounded bg-slate-200 px-1.5 py-0.5 text-[11px] font-medium text-slate-700 hover:bg-slate-300"
              >
                {copiedField === 'acc' ? '✓ Đã chép' : 'Sao chép'}
              </button>
            </div>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-500">Chủ tài khoản:</span>
            <span className="font-semibold text-slate-900">{accountName}</span>
          </div>

          <div className="flex items-center justify-between">
            <span className="text-slate-500">Số tiền nạp:</span>
            <span className="text-sm font-bold text-emerald-600">
              {formatNumber(amount)} đ ({formatNumber(amount)} điểm)
            </span>
          </div>

          <div className="flex items-center justify-between rounded border border-amber-200 bg-amber-50/70 p-2">
            <div>
              <span className="block font-semibold text-amber-900">Nội dung chuyển khoản (Bắt buộc):</span>
              <span className="font-mono text-sm font-extrabold text-blue-800">{transferContent}</span>
            </div>
            <button
              type="button"
              onClick={() => handleCopy(transferContent, 'content')}
              className="rounded bg-blue-600 px-2 py-1 text-xs font-semibold text-white shadow-sm hover:bg-blue-700"
            >
              {copiedField === 'content' ? '✓ Đã chép' : 'Sao chép'}
            </button>
          </div>
        </div>

        {/* Thông báo tự động Webhook */}
        <div className="rounded-lg bg-emerald-50 p-2.5 text-xs text-emerald-800 border border-emerald-200">
          <p className="font-semibold">⚡ Tự động cộng điểm 24/7</p>
          <p className="mt-0.5 text-[11px] leading-relaxed text-emerald-700">
            Hệ thống kết nối Webhook ngân hàng tự động nhận diện nội dung <strong>{transferContent}</strong> và cộng điểm vào ví trong 1 - 3 giây.
          </p>
        </div>

        {/* Kết quả kiểm tra số dư */}
        {checkMessage && (
          <div className="rounded-lg bg-blue-50 p-2.5 text-center text-xs font-medium text-blue-800 border border-blue-200">
            {checkMessage}
          </div>
        )}
      </div>
    </Dialog>
  );
}
