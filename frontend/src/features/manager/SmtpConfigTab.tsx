import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { SmtpConfig } from '../../shared/types/api';
import { formatDateTime } from '../../shared/utils/format';

function SmtpConfigEditor({ config }: { config: SmtpConfig }) {
  const queryClient = useQueryClient();
  const [enabled, setEnabled] = useState(config.enabled);
  const [host, setHost] = useState(config.host);
  const [port, setPort] = useState(String(config.port));
  const [username, setUsername] = useState(config.username);
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [fromAddress, setFromAddress] = useState(config.fromAddress);
  const [starttls, setStarttls] = useState(config.starttls);
  const [sslEnabled, setSslEnabled] = useState(config.sslEnabled);
  const [alertEmail, setAlertEmail] = useState(config.alertEmail ?? '');
  const [testEmail, setTestEmail] = useState(config.alertEmail ?? config.username ?? '');

  const [notice, setNotice] = useState<string | null>(null);
  const [testSuccess, setTestSuccess] = useState<string | null>(null);

  useEffect(() => {
    setEnabled(config.enabled);
    setHost(config.host);
    setPort(String(config.port));
    setUsername(config.username);
    setPassword('');
    setFromAddress(config.fromAddress);
    setStarttls(config.starttls);
    setSslEnabled(config.sslEnabled);
    setAlertEmail(config.alertEmail ?? '');
    setTestEmail((prev) => prev || config.alertEmail || config.username || '');
  }, [config]);

  const save = useMutation({
    mutationFn: api.updateSmtpConfig,
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.smtpConfig, updated);
      setPassword('');
      setNotice(
        updated.active
          ? 'Đã lưu và kích hoạt gửi email SMTP! Hệ thống sẽ dùng cấu hình này để gửi OTP quên mật khẩu và cảnh báo sự cố.'
          : 'Đã lưu cấu hình SMTP (đang ở chế độ tắt / ghi log demo).',
      );
    },
    onError: () => setNotice(null),
  });

  const reset = useMutation({
    mutationFn: api.resetSmtpConfig,
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.smtpConfig, updated);
      setPassword('');
      setNotice('Đã khôi phục cấu hình SMTP về giá trị mặc định trong .env.');
    },
    onError: () => setNotice(null),
  });

  const sendTest = useMutation({
    mutationFn: (email: string) => api.testSmtpConfig(email),
    onSuccess: (res) => {
      setTestSuccess(res.message);
    },
    onError: () => setTestSuccess(null),
  });

  function applyGmailPreset() {
    setEnabled(true);
    setHost('smtp.gmail.com');
    setPort('587');
    setStarttls(true);
    setSslEnabled(false);
    if (username.includes('@') && (!fromAddress || fromAddress === 'no-reply@parking.ptit.edu.vn')) {
      setFromAddress(username);
    }
    setNotice(null);
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setNotice(null);
    const parsedPort = Number(port);
    save.mutate({
      enabled,
      host: host.trim(),
      port: Number.isInteger(parsedPort) ? parsedPort : 587,
      username: username.trim(),
      password: password.trim() || undefined,
      fromAddress: fromAddress.trim() || undefined,
      starttls,
      sslEnabled,
      alertEmail: alertEmail.trim() || undefined,
    });
  }

  return (
    <div className="space-y-6">
      <Card
        title="Cấu hình gửi Email SMTP (Quên mật khẩu & Cảnh báo sự cố)"
        actions={
          <div className="flex flex-wrap items-center gap-2">
            {config.active ? (
              <Badge tone="green">SMTP đang hoạt động</Badge>
            ) : (
              <Badge tone="amber">Chế độ ghi log Demo</Badge>
            )}
            <Badge tone={config.source === 'DATABASE' ? 'blue' : 'gray'}>
              {config.source === 'DATABASE' ? 'Tùy chỉnh trên web' : 'Mặc định .env'}
            </Badge>
          </div>
        }
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          {notice && <Alert tone="success">{notice}</Alert>}
          {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
          {reset.isError && <Alert tone="error">{errorMessage(reset.error)}</Alert>}

          <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-blue-100 bg-blue-50/70 p-3 text-xs text-blue-900">
            <div className="space-y-0.5">
              <p className="font-semibold">Hướng dẫn nhanh với Gmail:</p>
              <p>
                Bật <strong>Xác minh 2 bước</strong> trong Google Account → tạo{' '}
                <strong>Mật khẩu ứng dụng (App Password 16 ký tự)</strong> và dán vào ô Mật khẩu bên dưới.
              </p>
            </div>
            <Button type="button" size="sm" variant="secondary" onClick={applyGmailPreset}>
              Điền nhanh Gmail (587)
            </Button>
          </div>

          <label className="flex items-start gap-2.5 text-sm font-medium text-slate-800">
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-slate-300"
            />
            <span>Bật gửi email thật qua SMTP (bỏ chọn để chỉ ghi log ra console máy chủ)</span>
          </label>

          <div className="grid gap-4 sm:grid-cols-12">
            <label className="block space-y-1 text-sm font-medium text-slate-700 sm:col-span-8">
              Máy chủ SMTP (Host)
              <input
                type="text"
                value={host}
                onChange={(e) => setHost(e.target.value)}
                placeholder="smtp.gmail.com"
                className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono"
              />
            </label>
            <label className="block space-y-1 text-sm font-medium text-slate-700 sm:col-span-4">
              Cổng (Port)
              <input
                type="number"
                min={1}
                max={65535}
                value={port}
                onChange={(e) => setPort(e.target.value)}
                placeholder="587"
                className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono"
              />
            </label>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block space-y-1 text-sm font-medium text-slate-700">
              Tài khoản gửi (Username / Gmail)
              <input
                type="email"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="example@gmail.com"
                className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </label>

            <div className="space-y-1">
              <label htmlFor="smtp-password" className="block text-sm font-medium text-slate-700">
                Mật khẩu / App Password
              </label>
              <div className="flex gap-2">
                <input
                  id="smtp-password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={config.hasPassword ? '•••••••••••• (để trống nếu giữ nguyên)' : 'Nhập App Password 16 ký tự'}
                  className="block min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm font-mono"
                />
                <Button type="button" size="sm" variant="secondary" onClick={() => setShowPassword((v) => !v)}>
                  {showPassword ? 'Ẩn' : 'Hiện'}
                </Button>
              </div>
              {config.hasPassword && (
                <p className="text-xs text-emerald-700">Đã lưu mật khẩu SMTP trên hệ thống.</p>
              )}
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block space-y-1 text-sm font-medium text-slate-700">
              Địa chỉ hiển thị người gửi (From)
              <input
                type="text"
                value={fromAddress}
                onChange={(e) => setFromAddress(e.target.value)}
                placeholder="Để trống để tự dùng tài khoản gửi"
                className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
            </label>

            <label className="block space-y-1 text-sm font-medium text-slate-700">
              Email nhận cảnh báo sự cố & khiếu nại
              <input
                type="email"
                value={alertEmail}
                onChange={(e) => setAlertEmail(e.target.value)}
                placeholder="quanly-baixe@gmail.com"
                className="block w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
              />
              <span className="block text-xs font-normal text-slate-500">
                Khi bảo vệ báo sự cố hoặc sinh viên gửi khiếu nại mới, hệ thống sẽ gửi email tới địa chỉ này (và email
                trên hồ sơ Quản lý).
              </span>
            </label>
          </div>

          <div className="flex flex-wrap gap-6">
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={starttls}
                onChange={(e) => setStarttls(e.target.checked)}
                className="h-4 w-4 rounded border-slate-300"
              />
              <span>Dùng STARTTLS (port 587 — khuyến nghị Gmail)</span>
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-700">
              <input
                type="checkbox"
                checked={sslEnabled}
                onChange={(e) => setSslEnabled(e.target.checked)}
                className="h-4 w-4 rounded border-slate-300"
              />
              <span>Dùng SSL trực tiếp (port 465)</span>
            </label>
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-1">
            <Button type="submit" loading={save.isPending}>
              Lưu cấu hình SMTP
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

          {config.updatedAt && (
            <p className="text-xs text-slate-500">Cập nhật lần cuối: {formatDateTime(config.updatedAt)}</p>
          )}
        </form>
      </Card>

      <Card title="Gửi email kiểm tra kết nối SMTP">
        <div className="space-y-3">
          {testSuccess && <Alert tone="success">{testSuccess}</Alert>}
          {sendTest.isError && <Alert tone="error">{errorMessage(sendTest.error)}</Alert>}
          <p className="text-sm text-slate-600">
            Sau khi bấm <strong>Lưu cấu hình SMTP</strong>, nhập một địa chỉ email nhận thử để kiểm tra gửi thư thật ngay
            lập tức.
          </p>
          <div className="flex flex-wrap gap-2">
            <input
              type="email"
              value={testEmail}
              onChange={(e) => setTestEmail(e.target.value)}
              placeholder="email-nhan-thu@gmail.com"
              className="min-w-[240px] flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
            <Button
              type="button"
              variant="secondary"
              loading={sendTest.isPending}
              onClick={() => {
                setTestSuccess(null);
                sendTest.mutate(testEmail);
              }}
            >
              Gửi email thử
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}

/** Tab cấu hình Email SMTP trong trang Quản lý. */
export function SmtpConfigTab() {
  const query = useQuery({ queryKey: queryKeys.smtpConfig, queryFn: api.smtpConfig });

  if (query.isPending) return <LoadingBlock label="Đang tải cấu hình SMTP..." />;
  if (query.isError) return <QueryError error={query.error} onRetry={() => void query.refetch()} />;

  return <SmtpConfigEditor config={query.data} />;
}
