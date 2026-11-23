import { useQuery, useQueryClient } from '@tanstack/react-query';
import QRCode from 'qrcode';
import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { Spinner } from '../../shared/components/Spinner';
import { formatNumber } from '../../shared/utils/format';
import { playSuccessSound } from '../../shared/utils/sound';
import { buildVietQrImageUrl, buildVietQrPayload, getBankShortName } from '../../shared/utils/vietQr';
import { useCurrentUser } from '../auth/session';

interface DepositPointsDialogProps {
  onClose: () => void;
  currentBalance?: number;
}

interface PaidSummary {
  previousBalance: number;
  newBalance: number;
  credited: number;
}

const PRESET_AMOUNTS = [10000, 20000, 50000, 100000, 200000];
const MIN_AMOUNT = 1000;
const MAX_AMOUNT = 50000000;
const POLL_INTERVAL_MS = 2000;

const DEFAULT_BANK_ID = 'MB';
const DEFAULT_ACCOUNT_NO = '0987654321';
const DEFAULT_ACCOUNT_NAME = 'BAI XE HOC VIEN PTIT';

export function DepositPointsDialog({ onClose, currentBalance }: DepositPointsDialogProps) {
  const queryClient = useQueryClient();
  const currentUser = useCurrentUser();

  const [amount, setAmount] = useState<number>(50000);
  const [debouncedAmount, setDebouncedAmount] = useState<number>(50000);
  const [customAmount, setCustomAmount] = useState<string>('');
  const [amountError, setAmountError] = useState<string | null>(null);

  const [copiedField, setCopiedField] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [checkMessage, setCheckMessage] = useState<string | null>(null);
  const [paidSummary, setPaidSummary] = useState<PaidSummary | null>(null);

  // Mốc số dư ban đầu khi mở hộp thoại để tự động phát hiện tiền vừa cộng
  const baselineBalanceRef = useRef<number | undefined>(currentBalance);

  // QR Code dạng base64 tạo cục bộ trên client (0ms delay, hoạt động cả khi offline & để tải ảnh QR)
  const [localQrDataUrl, setLocalQrDataUrl] = useState<string | null>(null);
  const [useLocalQr, setUseLocalQr] = useState<boolean>(false);
  const [imgLoaded, setImgLoaded] = useState<boolean>(false);

  // Debounce số tiền nhập tay 350ms để không gọi API liên tục từng phím
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (amount >= MIN_AMOUNT && amount <= MAX_AMOUNT) {
        setDebouncedAmount(amount);
      }
    }, 350);
    return () => window.clearTimeout(timer);
  }, [amount]);

  // Luôn lấy thông tin ngân hàng mới nhất từ backend (khi Quản lý vừa đổi tài khoản là cập nhật ngay)
  const depositQuery = useQuery({
    queryKey: queryKeys.depositInfo(debouncedAmount),
    queryFn: () => api.getDepositInfo(debouncedAmount),
    staleTime: 0,
    refetchOnMount: 'always',
    retry: 1,
  });

  // Tự động kiểm tra số dư ví mỗi 2 giây khi đang chờ chuyển khoản
  const walletPoll = useQuery({
    queryKey: queryKeys.myWallet,
    queryFn: api.myWallet,
    staleTime: 0,
    refetchInterval: paidSummary ? false : POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
  });

  // Phát hiện tự động ngay khi số dư tăng -> phát âm thanh + reload lịch sử giao dịch + báo thành công
  useEffect(() => {
    const latest = walletPoll.data?.balance;
    if (latest === undefined) return;

    if (baselineBalanceRef.current === undefined) {
      baselineBalanceRef.current = latest;
      return;
    }

    if (!paidSummary && latest > baselineBalanceRef.current) {
      const prev = baselineBalanceRef.current;
      const credited = latest - prev;
      playSuccessSound();
      void queryClient.invalidateQueries({ queryKey: queryKeys.myTransactionsAll });
      setPaidSummary({ previousBalance: prev, newBalance: latest, credited });
      setCheckMessage(null);
    }
  }, [walletPoll.data?.balance, paidSummary, queryClient]);

  // Xác định mã sinh viên & thông tin ngân hàng nhận tiền
  const studentCode =
    depositQuery.data?.studentCode ||
    (currentUser.user.username ? currentUser.user.username.toUpperCase() : 'B21DCCN001');

  const bankId = depositQuery.data?.bankId || DEFAULT_BANK_ID;
  const bankShortName = getBankShortName(bankId);
  const accountNo = depositQuery.data?.accountNo || DEFAULT_ACCOUNT_NO;
  const accountName = depositQuery.data?.accountName || DEFAULT_ACCOUNT_NAME;
  const transferContent = depositQuery.data?.transferContent || `NAP ${studentCode}`;

  const vietQrImageUrl =
    depositQuery.data?.qrCodeUrl ||
    buildVietQrImageUrl({
      bankId,
      accountNo,
      accountName,
      amount: debouncedAmount,
      message: transferContent,
    });

  useEffect(() => {
    setImgLoaded(false);
    setUseLocalQr(false);
  }, [vietQrImageUrl]);

  // Tự động sinh QR chuẩn Napas247 EMVCo cục bộ (0ms delay)
  useEffect(() => {
    const emvcoPayload = buildVietQrPayload({
      bankId,
      accountNo,
      amount: debouncedAmount,
      message: transferContent,
    });

    QRCode.toDataURL(emvcoPayload, {
      width: 280,
      margin: 2,
      color: { dark: '#0f172a', light: '#ffffff' },
    })
      .then((url) => setLocalQrDataUrl(url))
      .catch(() => setLocalQrDataUrl(null));
  }, [bankId, accountNo, debouncedAmount, transferContent]);

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
    setDebouncedAmount(value);
    setCustomAmount('');
    setAmountError(null);
    setCheckMessage(null);
  };

  const handleCustomChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/\D/g, '');
    setCustomAmount(raw);
    setCheckMessage(null);
    if (!raw) {
      setAmountError(null);
      return;
    }
    const parsed = Number(raw);
    if (parsed < MIN_AMOUNT) {
      setAmountError(`Số tiền tối thiểu là ${formatNumber(MIN_AMOUNT)} đ`);
    } else if (parsed > MAX_AMOUNT) {
      setAmountError(`Số tiền tối đa là ${formatNumber(MAX_AMOUNT)} đ`);
    } else {
      setAmountError(null);
      setAmount(parsed);
    }
  };

  const handleManualCheck = async () => {
    setChecking(true);
    setCheckMessage(null);
    try {
      const result = await walletPoll.refetch();
      void queryClient.invalidateQueries({ queryKey: queryKeys.myTransactionsAll });
      const latest = result.data?.balance;
      const baseline = baselineBalanceRef.current;

      if (latest !== undefined && baseline !== undefined && latest <= baseline) {
        setCheckMessage(
          `Số dư hiện tại: ${formatNumber(latest)} điểm — Chưa ghi nhận giao dịch mới. Nếu bạn vừa chuyển khoản, hệ thống đang tự động kiểm tra mỗi 2 giây.`,
        );
      }
    } catch {
      setCheckMessage('Không thể kết nối máy chủ để kiểm tra số dư.');
    } finally {
      setChecking(false);
    }
  };

  const handleDownloadQr = () => {
    if (!localQrDataUrl) return;
    const link = document.createElement('a');
    link.href = localQrDataUrl;
    link.download = `VietQR-${studentCode}-${debouncedAmount}.png`;
    link.click();
  };

  const handleDepositMore = () => {
    if (paidSummary) {
      baselineBalanceRef.current = paidSummary.newBalance;
    }
    setPaidSummary(null);
    setCheckMessage(null);
  };

  // Màn hình chúc mừng ngay khi phát hiện nạp tiền thành công
  if (paidSummary) {
    return (
      <Dialog
        title="Nạp điểm thành công"
        onClose={onClose}
        footer={
          <div className="flex w-full items-center justify-between gap-2">
            <Button size="sm" variant="secondary" onClick={handleDepositMore}>
              + Nạp thêm
            </Button>
            <Button size="sm" onClick={onClose}>
              Hoàn tất
            </Button>
          </div>
        }
      >
        <div className="flex flex-col items-center space-y-4 py-3 text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-3xl text-emerald-600 shadow-sm">
            ✓
          </div>
          <div>
            <p className="text-sm font-medium text-slate-600">Tiền đã vào ví tự động</p>
            <p className="mt-1 text-3xl font-extrabold text-emerald-600">
              +{formatNumber(paidSummary.credited)} điểm
            </p>
          </div>
          <div className="w-full rounded-xl border border-emerald-200 bg-emerald-50/70 p-3.5 text-sm">
            <div className="flex justify-between py-1">
              <span className="text-slate-600">Số dư trước</span>
              <span className="font-medium text-slate-800">{formatNumber(paidSummary.previousBalance)} điểm</span>
            </div>
            <div className="flex justify-between border-t border-emerald-200/80 pt-2 mt-1">
              <span className="font-semibold text-slate-800">Số dư mới</span>
              <span className="text-base font-bold text-blue-700">{formatNumber(paidSummary.newBalance)} điểm</span>
            </div>
          </div>
          <p className="text-xs text-slate-500">Số dư và lịch sử giao dịch trên trang đã được cập nhật tự động.</p>
        </div>
      </Dialog>
    );
  }

  return (
    <Dialog
      title="Nạp điểm ví bằng VietQR (Napas247)"
      onClose={onClose}
      footer={
        <div className="flex w-full items-center justify-between gap-2">
          <Button size="sm" variant="secondary" onClick={handleManualCheck} disabled={checking}>
            {checking ? 'Đang kiểm tra...' : '🔄 Kiểm tra ngay'}
          </Button>
          <Button size="sm" onClick={onClose}>
            Đóng
          </Button>
        </div>
      }
    >
      <div className="space-y-4 text-sm text-slate-700">
        {/* Thanh trạng thái tự động lắng nghe tiền về */}
        <div className="flex items-center justify-between rounded-lg border border-emerald-200 bg-emerald-50/80 px-3 py-2 text-xs text-emerald-800">
          <div className="flex items-center gap-2">
            <span className="relative flex h-2.5 w-2.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
              <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500" />
            </span>
            <span className="font-medium">Đang tự động chờ tiền về (tự cập nhật mỗi 2 giây)</span>
          </div>
          <span className="font-mono text-[11px] font-semibold text-emerald-700">
            {formatNumber(walletPoll.data?.balance ?? currentBalance ?? 0)} đ
          </span>
        </div>

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
              placeholder="VD: 2000, 30000..."
              value={customAmount}
              onChange={handleCustomChange}
              className={`flex-1 rounded-md border px-2.5 py-1 text-xs focus:outline-none ${
                amountError ? 'border-red-400 focus:border-red-500' : 'border-slate-300 focus:border-blue-500'
              }`}
            />
            <span className="text-xs font-medium text-slate-500">VNĐ</span>
          </div>
          {amountError && <p className="mt-1 text-xs text-red-600">{amountError}</p>}
        </div>

        {/* Mã VietQR động */}
        <div className="flex flex-col items-center rounded-xl border border-blue-100 bg-blue-50/40 p-4">
          <div className="relative flex h-56 w-56 items-center justify-center rounded-lg border border-slate-200 bg-white p-2 shadow-sm">
            {!useLocalQr ? (
              <>
                {!imgLoaded && localQrDataUrl && (
                  <img
                    src={localQrDataUrl}
                    alt="Đang tải thẻ VietQR"
                    className="absolute inset-2 h-52 w-52 object-contain opacity-90"
                  />
                )}
                <img
                  src={vietQrImageUrl}
                  alt="VietQR Napas247 Nạp Tiền"
                  className={`h-full w-full object-contain transition-opacity duration-200 ${
                    imgLoaded ? 'opacity-100' : 'opacity-0'
                  }`}
                  onLoad={() => setImgLoaded(true)}
                  onError={() => setUseLocalQr(true)}
                />
              </>
            ) : localQrDataUrl ? (
              <div className="flex flex-col items-center">
                <img src={localQrDataUrl} alt="Mã QR Napas247 cục bộ" className="h-44 w-44 object-contain" />
                <span className="text-[10px] font-medium text-slate-500">Mã QR Napas247 cục bộ</span>
              </div>
            ) : (
              <Spinner className="h-8 w-8 text-blue-600" />
            )}
          </div>

          <div className="mt-2.5 flex flex-wrap items-center justify-center gap-3">
            <p className="text-xs font-medium text-slate-600">Quét bằng app ngân hàng hoặc Momo / VNPay</p>
            <div className="flex items-center gap-2">
              {localQrDataUrl && (
                <>
                  <button
                    type="button"
                    onClick={handleDownloadQr}
                    className="rounded bg-white px-2 py-0.5 text-[11px] font-semibold text-blue-700 shadow-sm ring-1 ring-slate-200 hover:bg-blue-50"
                  >
                    ⬇ Lưu mã QR
                  </button>
                  <button
                    type="button"
                    onClick={() => setUseLocalQr((prev) => !prev)}
                    className="text-[11px] text-blue-600 hover:underline"
                  >
                    {useLocalQr ? 'Hiển thị mẫu thẻ' : 'QR gọn'}
                  </button>
                </>
              )}
            </div>
          </div>
        </div>

        {/* Thông tin chuyển khoản chi tiết */}
        <div className="space-y-2 rounded-lg border border-slate-200 bg-slate-50/60 p-3 text-xs">
          <div className="flex items-center justify-between">
            <span className="text-slate-500">Ngân hàng:</span>
            <span className="font-semibold text-slate-900">
              {bankId} {bankShortName !== bankId ? `(${bankShortName})` : ''}
            </span>
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
              {formatNumber(debouncedAmount)} đ ({formatNumber(debouncedAmount)} điểm)
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

        {/* Kết quả kiểm tra thủ công (nếu người dùng bấm nút) */}
        {checkMessage && (
          <div className="rounded-lg border border-blue-200 bg-blue-50 p-2.5 text-center text-xs font-medium text-blue-800">
            {checkMessage}
          </div>
        )}
      </div>
    </Dialog>
  );
}
