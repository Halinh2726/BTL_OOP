import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { SelectField, TextField } from '../../shared/components/Field';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock, Spinner } from '../../shared/components/Spinner';
import { useFieldErrors } from '../../shared/hooks/useFieldErrors';
import type { BankConfig, ParkingPolicyConfig, VietQrTemplate } from '../../shared/types/api';
import { formatDateTime } from '../../shared/utils/format';
import {
  VIETNAM_BANKS,
  buildVietQrImageUrl,
  buildVietQrPayload,
  getBankShortName,
  normalizeBankAccountName,
} from '../../shared/utils/vietQr';

const TEMPLATES: { value: VietQrTemplate; label: string }[] = [
  { value: 'compact2', label: 'Đầy đủ thông tin (compact2 - khuyến nghị)' },
  { value: 'compact', label: 'Gọn nhẹ kèm logo (compact)' },
  { value: 'qr_only', label: 'Chỉ mã QR (qr_only)' },
  { value: 'print', label: 'Bản in tại quầy (print)' },
];

const bankConfigSchema = z.object({
  bankId: z
    .string()
    .trim()
    .min(2, 'Vui lòng chọn hoặc nhập mã ngân hàng')
    .max(20, 'Mã ngân hàng tối đa 20 ký tự')
    .regex(/^[A-Za-z0-9]{2,20}$/, 'Mã ngân hàng chỉ gồm chữ và số (VD: MB, VCB, ACB)'),
  accountNo: z
    .string()
    .trim()
    .transform((v) => v.replace(/[\s.-]/g, '').toUpperCase())
    .pipe(z.string().regex(/^[A-Z0-9]{4,30}$/, 'Số tài khoản gồm 4–30 ký tự chữ hoặc số')),
  accountName: z.string().trim().min(1, 'Vui lòng nhập tên chủ tài khoản').max(100, 'Tối đa 100 ký tự'),
  template: z.enum(['compact2', 'compact', 'qr_only', 'print']),
});

type BankConfigForm = z.infer<typeof bankConfigSchema>;

const KNOWN_BANK_IDS = new Set(VIETNAM_BANKS.map((b) => b.id));

/** Tab Quản lý cấu hình tài khoản ngân hàng nhận tiền VietQR Napas247. */
export function BankConfigTab() {
  const query = useQuery({ queryKey: queryKeys.bankConfig, queryFn: api.bankConfig });

  if (query.isPending) return <LoadingBlock label="Đang tải cấu hình ngân hàng..." />;
  if (query.isError) return <QueryError error={query.error} onRetry={() => void query.refetch()} />;

  return <BankConfigEditor config={query.data} />;
}

function BankConfigEditor({ config }: { config: BankConfig }) {
  const queryClient = useQueryClient();
  const [customBank, setCustomBank] = useState(!KNOWN_BANK_IDS.has(config.bankId));
  const [notice, setNotice] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const form = useForm<BankConfigForm>({
    resolver: zodResolver(bankConfigSchema),
    defaultValues: {
      bankId: config.bankId,
      accountNo: config.accountNo,
      accountName: config.accountName,
      template: config.template,
    },
  });

  useEffect(() => {
    form.reset({
      bankId: config.bankId,
      accountNo: config.accountNo,
      accountName: config.accountName,
      template: config.template,
    });
    setCustomBank(!KNOWN_BANK_IDS.has(config.bankId));
  }, [config, form]);

  const applyFieldErrors = useFieldErrors(form.setError, ['bankId', 'accountNo', 'accountName', 'template']);

  const save = useMutation({
    mutationFn: api.updateBankConfig,
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.bankConfig, updated);
      void queryClient.invalidateQueries({ queryKey: ['me', 'deposit-info'] });
      setNotice('Đã lưu cấu hình ngân hàng! Mã VietQR của sinh viên và cổng bảo vệ sẽ áp dụng ngay lập tức.');
    },
    onError: (err) => {
      setNotice(null);
      applyFieldErrors(err);
    },
  });

  const reset = useMutation({
    mutationFn: api.resetBankConfig,
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.bankConfig, updated);
      void queryClient.invalidateQueries({ queryKey: ['me', 'deposit-info'] });
      setNotice('Đã khôi phục về tài khoản ngân hàng mặc định trong file .env.');
    },
    onError: () => setNotice(null),
  });

  const watched = form.watch();
  const previewBankId = (watched.bankId || config.bankId).trim().toUpperCase();
  const previewAccountNo = (watched.accountNo || config.accountNo).trim().replace(/[\s.-]/g, '').toUpperCase();
  const previewAccountName = normalizeBankAccountName(watched.accountName || config.accountName);
  const previewTemplate = watched.template || 'compact2';
  const previewValid = /^[A-Z0-9]{2,20}$/.test(previewBankId) && /^[A-Z0-9]{4,30}$/.test(previewAccountNo);

  const [previewQrFallback, setPreviewQrFallback] = useState<string | null>(null);
  const [imgFailed, setImgFailed] = useState(false);

  const previewUrl = previewValid
    ? buildVietQrImageUrl({
        bankId: previewBankId,
        accountNo: previewAccountNo,
        accountName: previewAccountName || 'BAI XE PTIT',
        template: previewTemplate,
        amount: 50000,
        message: 'NAP B21DCCN001',
      })
    : null;

  useEffect(() => {
    setImgFailed(false);
    if (!previewValid) {
      setPreviewQrFallback(null);
      return;
    }
    const payload = buildVietQrPayload({
      bankId: previewBankId,
      accountNo: previewAccountNo,
      amount: 50000,
      message: 'NAP B21DCCN001',
    });
    void QRCode.toDataURL(payload, { width: 220, margin: 1 })
      .then(setPreviewQrFallback)
      .catch(() => setPreviewQrFallback(null));
  }, [previewValid, previewBankId, previewAccountNo, previewTemplate, previewAccountName]);

  const copyText = (text: string, key: string) => {
    if (!navigator.clipboard) return;
    void navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 1800);
  };

  const { errors } = form.formState;

  return (
    <div className="space-y-6">
      <div className="grid items-start gap-6 lg:grid-cols-12">
        {/* Form cấu hình ngân hàng */}
        <Card
          className="lg:col-span-7"
          title="Cấu hình tài khoản ngân hàng nhận tiền (VietQR)"
          actions={
            config.source === 'DATABASE' ? (
              <Badge tone="green">Tùy chỉnh thủ công</Badge>
            ) : (
              <Badge tone="blue">Mặc định từ .env</Badge>
            )
          }
        >
          <form
            className="space-y-4"
            noValidate
            onSubmit={form.handleSubmit((values) => {
              setNotice(null);
              save.mutate({
                bankId: values.bankId.trim().toUpperCase(),
                accountNo: values.accountNo.trim().replace(/[\s.-]/g, '').toUpperCase(),
                accountName: normalizeBankAccountName(values.accountName),
                template: values.template,
              });
            })}
          >
            {notice && <Alert tone="success">{notice}</Alert>}
            {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
            {reset.isError && <Alert tone="error">{errorMessage(reset.error)}</Alert>}

            <div className="space-y-2">
              <SelectField
                label="Ngân hàng nhận tiền"
                value={customBank ? '__CUSTOM__' : watched.bankId}
                onChange={(e) => {
                  const val = e.target.value;
                  if (val === '__CUSTOM__') {
                    setCustomBank(true);
                    form.setValue('bankId', '', { shouldDirty: true });
                  } else {
                    setCustomBank(false);
                    form.setValue('bankId', val, { shouldValidate: true, shouldDirty: true });
                  }
                }}
                error={!customBank ? errors.bankId?.message : undefined}
              >
                {VIETNAM_BANKS.map((bank) => (
                  <option key={bank.id} value={bank.id}>
                    {bank.id} — {bank.shortName} ({bank.name})
                  </option>
                ))}
                <option value="__CUSTOM__">Khác (nhập mã ngân hàng VietQR thủ công...)</option>
              </SelectField>

              {customBank && (
                <TextField
                  label="Mã ngân hàng (theo chuẩn VietQR)"
                  placeholder="VD: MB, VCB, ACB, TCB..."
                  error={errors.bankId?.message}
                  {...form.register('bankId')}
                />
              )}
            </div>

            <TextField
              label="Số tài khoản ngân hàng"
              placeholder="Nhập số tài khoản nhận tiền (đã liên kết SePay/Casso)"
              error={errors.accountNo?.message}
              hint="Tự động loại bỏ khoảng trắng và dấu gạch ngang khi lưu."
              {...form.register('accountNo')}
            />

            <TextField
              label="Tên chủ tài khoản"
              placeholder="VD: NGUYEN VAN A hoặc BAI XE HOC VIEN PTIT"
              error={errors.accountName?.message}
              hint="Tự động chuyển sang chữ IN HOA không dấu chuẩn ngân hàng."
              {...form.register('accountName')}
            />

            <SelectField label="Mẫu ảnh mã QR (VietQR)" error={errors.template?.message} {...form.register('template')}>
              {TEMPLATES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </SelectField>

            <div className="flex flex-wrap items-center gap-3 pt-2">
              <Button type="submit" loading={save.isPending}>
                Lưu tài khoản ngân hàng
              </Button>
              {config.source === 'DATABASE' && (
                <Button
                  type="button"
                  variant="secondary"
                  loading={reset.isPending}
                  onClick={() => {
                    setNotice(null);
                    reset.mutate();
                  }}
                >
                  Khôi phục mặc định (.env)
                </Button>
              )}
            </div>

            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600 space-y-1">
              <p>
                <span className="font-semibold text-slate-700">Mặc định .env:</span> {config.envDefault.bankId} —{' '}
                <span className="font-mono">{config.envDefault.accountNo}</span> ({config.envDefault.accountName})
              </p>
              {config.updatedAt && (
                <p>
                  <span className="font-semibold text-slate-700">Cập nhật lần cuối:</span>{' '}
                  {formatDateTime(config.updatedAt)}
                </p>
              )}
            </div>
          </form>
        </Card>

        {/* Xem trước mã QR trực tiếp */}
        <Card className="lg:col-span-5" title="Xem trước mã VietQR (mẫu 50.000đ)">
          <div className="flex flex-col items-center space-y-4">
            <div className="flex h-60 w-60 items-center justify-center rounded-xl border border-slate-200 bg-white p-2 shadow-sm">
              {previewValid && previewUrl && !imgFailed ? (
                <img
                  key={previewUrl}
                  src={previewUrl}
                  alt="Xem trước mã VietQR"
                  className="h-full w-full object-contain"
                  onError={() => setImgFailed(true)}
                />
              ) : previewQrFallback ? (
                <img src={previewQrFallback} alt="Mã QR dự phòng" className="h-52 w-52 object-contain" />
              ) : (
                <div className="flex flex-col items-center gap-2 text-center text-xs text-slate-400">
                  <Spinner className="h-6 w-6 text-slate-400" />
                  <span>Nhập đủ mã ngân hàng & số tài khoản để xem trước</span>
                </div>
              )}
            </div>

            <dl className="w-full space-y-1 text-sm">
              <InfoRow label="Ngân hàng" value={`${previewBankId} (${getBankShortName(previewBankId)})`} />
              <InfoRow label="Số tài khoản" value={<span className="font-mono font-bold">{previewAccountNo || '—'}</span>} />
              <InfoRow label="Chủ tài khoản" value={previewAccountName || '—'} />
              <InfoRow label="Nội dung mẫu" value={<span className="font-mono text-blue-700">NAP B21DCCN001</span>} />
            </dl>

            <p className="text-center text-xs text-slate-500">
              Mẹo: Dùng app ngân hàng quét thử mã này trước khi bấm <strong>Lưu</strong> để chắc chắn đúng tên chủ tài khoản.
            </p>
          </div>
        </Card>
      </div>

      {/* Thông tin Webhook cho Quản lý / Demo */}
      <Card title="Thông tin Webhook ngân hàng tự động (SePay / Casso)">
        <div className="grid gap-4 text-sm sm:grid-cols-2">
          <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-1.5">
            <p className="text-xs font-semibold uppercase tracking-wider text-slate-500">Đường dẫn Webhook SePay</p>
            <div className="flex items-center justify-between gap-2">
              <code className="truncate font-mono text-xs font-semibold text-blue-800">/api/v1/webhooks/sepay</code>
              <button
                type="button"
                onClick={() => copyText('/api/v1/webhooks/sepay', 'sepay')}
                className="shrink-0 rounded bg-slate-200 px-2 py-0.5 text-xs font-medium text-slate-700 hover:bg-slate-300"
              >
                {copiedKey === 'sepay' ? '✓ Đã chép' : 'Sao chép'}
              </button>
            </div>
            <p className="text-xs text-slate-500">
              Casso dùng <code className="font-mono">/api/v1/webhooks/casso</code>. Khóa bảo mật cấu hình ở biến{' '}
              <code className="font-mono">WEBHOOK_API_KEY</code> trong file <code className="font-mono">.env</code>.
            </p>
          </div>

          <div className="rounded-lg border border-emerald-200 bg-emerald-50/70 p-3 space-y-1.5">
            <p className="text-xs font-semibold uppercase tracking-wider text-emerald-800">Cú pháp nội dung chuyển khoản</p>
            <ul className="space-y-1 text-xs text-emerald-900">
              <li>
                • <code className="rounded bg-white px-1.5 py-0.5 font-mono font-bold">NAP &lt;MSSV&gt;</code> — Tự động cộng
                điểm vào ví sinh viên.
              </li>
              <li>
                • <code className="rounded bg-white px-1.5 py-0.5 font-mono font-bold">TTGX &lt;ID&gt;</code> — Tự động thanh
                toán lượt gửi xe đang mở tại cổng.
              </li>
            </ul>
          </div>
        </div>
      </Card>

      {/* Cấu hình giờ khóa lấy xe & phí qua đêm */}
      <ParkingPolicyCard />
    </div>
  );
}

/** Cấu hình giờ khóa lấy xe trong ngày (phí qua đêm của từng loại xe được chỉnh ở tab Giá vé lượt). */
function ParkingPolicyCard() {
  const queryClient = useQueryClient();
  const policyQuery = useQuery({ queryKey: queryKeys.parkingPolicy, queryFn: api.parkingPolicy });
  const [curfewEnabled, setCurfewEnabled] = useState(true);
  const [checkoutCurfewTime, setCheckoutCurfewTime] = useState('22:00');
  const [notice, setNotice] = useState<string | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (!policyQuery.data) return;
    setCurfewEnabled(policyQuery.data.curfewEnabled);
    setCheckoutCurfewTime(policyQuery.data.checkoutCurfewTime);
  }, [policyQuery.data]);

  const save = useMutation({
    mutationFn: api.updateParkingPolicy,
    onSuccess: (updated: ParkingPolicyConfig) => {
      queryClient.setQueryData(queryKeys.parkingPolicy, updated);
      setNotice(
        `Đã lưu chính sách: ${updated.curfewEnabled ? `khóa lấy xe từ ${updated.checkoutCurfewTime}` : 'tắt khóa lấy xe trong ngày'}.`,
      );
      setValidationError(null);
    },
    onError: () => setNotice(null),
  });

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setNotice(null);
    if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(checkoutCurfewTime)) {
      setValidationError('Giờ khóa phải đúng định dạng HH:mm (ví dụ: 22:00).');
      return;
    }
    setValidationError(null);
    save.mutate({
      curfewEnabled,
      checkoutCurfewTime,
      overnightFeePoints: policyQuery.data?.overnightFeePoints ?? 15000,
    });
  }

  return (
    <Card title="Cấu hình giờ khóa lấy xe trong ngày">
      {policyQuery.isPending && <LoadingBlock label="Đang tải cấu hình giờ khóa..." />}
      {policyQuery.isError && <QueryError error={policyQuery.error} onRetry={() => void policyQuery.refetch()} />}
      {policyQuery.data && (
        <form onSubmit={handleSubmit} className="space-y-4">
          {notice && <Alert tone="success">{notice}</Alert>}
          {validationError && <Alert tone="error">{validationError}</Alert>}
          {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}

          <label className="flex items-start gap-2.5 text-sm font-medium text-slate-800">
            <input
              type="checkbox"
              className="mt-0.5 h-4 w-4 rounded border-slate-300"
              checked={curfewEnabled}
              onChange={(event) => setCurfewEnabled(event.target.checked)}
            />
            <span>Bật khóa lấy xe theo giờ quy định trong ngày (giờ Việt Nam)</span>
          </label>

          <div className="max-w-sm space-y-1">
            <label htmlFor="checkout-curfew-time" className="block text-sm font-medium text-slate-700">
              Giờ bắt đầu khóa lấy xe
            </label>
            <input
              id="checkout-curfew-time"
              type="time"
              value={checkoutCurfewTime}
              onChange={(event) => setCheckoutCurfewTime(event.target.value)}
              disabled={!curfewEnabled}
              className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:bg-slate-100"
            />
            <p className="text-xs text-slate-500">
              Mặc định 22:00 — sau giờ này cổng không cho xác nhận xe ra. Mức phí qua đêm của từng loại xe được chỉnh tại tab <strong>Giá vé lượt</strong>.
            </p>
          </div>

          <Button type="submit" loading={save.isPending}>
            Lưu cấu hình giờ khóa
          </Button>
        </form>
      )}
    </Card>
  );
}
