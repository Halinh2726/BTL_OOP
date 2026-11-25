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
    refetchOnWindowFocus: true,
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
  };

  const handleCustomChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/\D/g, '');
    setCustomAmount(raw);
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
            <div className="mt-1 flex justify-between border-t border-emerald-200/80 pt-2">
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
      maxWidthClass="max-w-[840px]"
      footer={
        <div className="flex w-full items-center justify-between gap-3">
          <span className="text-xs text-slate-500 sm:text-sm">
            Hệ thống tự động kiểm tra và cộng điểm ngay khi nhận chuyển khoản.
          </span>
          <Button size="md" variant="secondary" onClick={onClose}>
            Tắt mã QR
          </Button>
        </div>
      }
    >
      <div className="grid gap-5 text-sm text-slate-700 sm:grid-cols-12">
        {/* Cột trái: Mã QR và thao tác nhanh */}
        <div className="flex flex-col items-center justify-center rounded-xl border border-blue-100 bg-gradient-to-b from-blue-50/60 to-slate-50/60 p-5 sm:col-span-5">
          <div className="relative flex h-56 w-56 items-center justify-center rounded-xl border border-slate-200 bg-white p-2.5 shadow-sm sm:h-64 sm:w-64">
            {!useLocalQr ? (
              <>
                {!imgLoaded && localQrDataUrl && (
                  <img
                    src={localQrDataUrl}
                    alt="Đang tải thẻ VietQR"
                    className="absolute inset-2.5 h-[calc(100%-1.25rem)] w-[calc(100%-1.25rem)] object-contain opacity-90"
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
                <img src={localQrDataUrl} alt="Mã QR Napas247 cục bộ" className="h-52 w-52 object-contain" />
                <span className="text-[11px] font-medium text-slate-500">Mã QR Napas247 cục bộ</span>
              </div>
            ) : (
              <Spinner className="h-8 w-8 text-blue-600" />
            )}
          </div>

          <p className="mt-3 text-center text-xs font-medium text-slate-600 sm:text-sm">
            Quét bằng app ngân hàng, Momo hoặc VNPay
          </p>

          {localQrDataUrl && (
            <div className="mt-2.5 flex flex-wrap items-center justify-center gap-2">
              <button
                type="button"
                onClick={handleDownloadQr}
                className="rounded-lg bg-white px-3 py-1.5 text-xs font-semibold text-blue-700 shadow-sm ring-1 ring-slate-200 hover:bg-blue-50"
              >
                ⬇ Lưu mã QR
              </button>
              <button
                type="button"
                onClick={() => setUseLocalQr((prev) => !prev)}
                className="rounded-lg px-2.5 py-1.5 text-xs font-medium text-blue-600 hover:bg-blue-50 hover:underline"
              >
                {useLocalQr ? 'Mẫu thẻ' : 'QR gọn'}
              </button>
            </div>
          )}
        </div>

        {/* Cột phải: Trạng thái tự động, chọn mệnh giá và thông tin chuyển khoản */}
        <div className="flex flex-col justify-between space-y-4 sm:col-span-7">
          {/* Trạng thái lắng nghe giao dịch tự động */}
          <div className="flex items-center justify-between gap-2 rounded-xl border border-emerald-200 bg-emerald-50/85 px-3.5 py-2.5 text-xs text-emerald-800 sm:text-sm">
            <div className="flex items-center gap-2">
              <span className="relative flex h-2.5 w-2.5 shrink-0">
                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-75" />
                <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-emerald-500" />
              </span>
              <span className="font-medium">Tự động kiểm tra giao dịch (2s/lần)</span>
              {walletPoll.isFetching && <Spinner className="h-3.5 w-3.5 text-emerald-600" />}
            </div>
            <span className="shrink-0 font-mono text-xs font-bold text-emerald-700 sm:text-sm">
              {formatNumber(walletPoll.data?.balance ?? currentBalance ?? 0)} đ
            </span>
          </div>

          {/* Chọn số tiền nạp */}
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-500">
              Chọn nhanh mệnh giá
            </label>
            <div className="mt-2 grid grid-cols-3 gap-2 sm:grid-cols-5">
              {PRESET_AMOUNTS.map((val) => (
                <button
                  key={val}
                  type="button"
                  onClick={() => handleSelectPreset(val)}
                  className={`rounded-lg border px-2 py-2 text-center text-xs font-semibold transition sm:text-sm ${
                    amount === val && !customAmount
                      ? 'border-blue-600 bg-blue-50 text-blue-700 shadow-sm'
                      : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                  }`}
                >
                  {formatNumber(val)}
                </button>
              ))}
            </div>

            <div className="mt-2.5 flex items-center gap-2">
              <span className="shrink-0 text-xs text-slate-500 sm:text-sm">Số khác:</span>
              <input
                type="text"
                inputMode="numeric"
                placeholder="VD: 30000"
                value={customAmount}
                onChange={handleCustomChange}
                className={`min-w-0 flex-1 rounded-lg border px-3 py-1.5 text-sm focus:outline-none ${
                  amountError ? 'border-red-400 focus:border-red-500' : 'border-slate-300 focus:border-blue-500'
                }`}
              />
              <span className="text-xs font-medium text-slate-500 sm:text-sm">VNĐ</span>
            </div>
            {amountError && <p className="mt-1 text-xs text-red-600">{amountError}</p>}
          </div>

          {/* Thông tin chuyển khoản chi tiết */}
          <div className="space-y-2 rounded-xl border border-slate-200 bg-slate-50/70 p-4 text-xs sm:text-sm">
            <div className="flex items-center justify-between gap-2">
              <span className="text-slate-500">Ngân hàng</span>
              <span className="font-semibold text-slate-900">
                {bankId} {bankShortName !== bankId ? `(${bankShortName})` : ''}
              </span>
            </div>

            <div className="flex items-center justify-between gap-2">
              <span className="text-slate-500">Số tài khoản</span>
              <div className="flex items-center gap-2">
                <span className="font-mono text-sm font-bold text-blue-700 sm:text-base">{accountNo}</span>
                <button
                  type="button"
                  onClick={() => handleCopy(accountNo, 'acc')}
                  className="rounded bg-slate-200 px-2 py-0.5 text-xs font-medium text-slate-700 hover:bg-slate-300"
                >
                  {copiedField === 'acc' ? '✓ Đã chép' : 'Sao chép'}
                </button>
              </div>
            </div>

            <div className="flex items-center justify-between gap-2">
              <span className="text-slate-500">Chủ tài khoản</span>
              <span className="truncate font-semibold text-slate-900">{accountName}</span>
            </div>

            <div className="flex items-center justify-between gap-2">
              <span className="text-slate-500">Số tiền nạp</span>
              <span className="text-sm font-bold text-emerald-600 sm:text-base">
                {formatNumber(debouncedAmount)} đ ({formatNumber(debouncedAmount)} điểm)
              </span>
            </div>

            <div className="mt-1.5 flex items-center justify-between gap-2 rounded-lg border border-amber-200 bg-amber-50/80 p-2.5">
              <div className="min-w-0">
                <span className="block text-xs font-semibold text-amber-900">
                  Nội dung chuyển khoản (Bắt buộc)
                </span>
                <span className="font-mono text-sm font-extrabold text-blue-800 sm:text-base">{transferContent}</span>
              </div>
              <button
                type="button"
                onClick={() => handleCopy(transferContent, 'content')}
                className="shrink-0 rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white shadow-sm hover:bg-blue-700 sm:text-sm"
              >
                {copiedField === 'content' ? '✓ Đã chép' : 'Sao chép'}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Dialog>
  );
}
