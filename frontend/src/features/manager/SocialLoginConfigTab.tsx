import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { TextField } from '../../shared/components/Field';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { SocialProvider, SocialProviderConfig, UpdateSocialProviderConfig } from '../../shared/types/api';
import { SocialProviderLogo } from '../auth/SocialProviderLogo';
import { SOCIAL_PROVIDER_LABEL } from '../auth/socialAuth';

const CONSOLE: Record<SocialProvider, { href: string; label: string }> = {
  google: { href: 'https://console.cloud.google.com/auth/clients', label: 'Mở Google Cloud' },
  microsoft: { href: 'https://entra.microsoft.com/', label: 'Mở Microsoft Entra' },
};

export function SocialLoginConfigTab() {
  const config = useQuery({ queryKey: queryKeys.socialLoginConfig, queryFn: api.socialLoginConfig });
  if (config.isPending) return <LoadingBlock label="Đang tải cấu hình đăng nhập..." />;
  if (config.isError) return <QueryError error={config.error} onRetry={() => void config.refetch()} />;

  const origin = config.data[0] && new URL(config.data[0].redirectUri).origin;
  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-600">
        Tạo ứng dụng OAuth loại Web, thêm URL chuyển hướng của nhà cung cấp, rồi nhập Client ID và Client Secret.
        {origin && <> Khi thử đăng nhập, mở hệ thống tại <strong className="break-all">{origin}</strong>.</>}
      </p>
      <div className="grid items-start gap-4 xl:grid-cols-2">
        {config.data.map((provider) => <ProviderConfigCard key={provider.provider} config={provider} />)}
      </div>
    </div>
  );
}

function ProviderConfigCard({ config }: { config: SocialProviderConfig }) {
  const queryClient = useQueryClient();
  const [clientId, setClientId] = useState(config.clientId);
  const [clientSecret, setClientSecret] = useState('');
  const [enabled, setEnabled] = useState(config.enabled || (!config.managed && !config.hasSecret));
  const [copyMessage, setCopyMessage] = useState('');
  const [pendingAction, setPendingAction] = useState<'save' | 'reset' | null>(null);
  const [notice, setNotice] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const consoleLink = CONSOLE[config.provider];

  async function update(action: 'save' | 'reset', body?: UpdateSocialProviderConfig) {
    setPendingAction(action);
    setNotice(null);
    try {
      const saved = body
        ? await api.updateSocialLoginConfig(config.provider, body)
        : await api.resetSocialLoginConfig(config.provider);
      setClientId(saved.clientId);
      setEnabled(saved.enabled);
      queryClient.setQueryData<SocialProviderConfig[]>(queryKeys.socialLoginConfig,
        (current) => current?.map((item) => item.provider === saved.provider ? saved : item));
      void queryClient.invalidateQueries({ queryKey: queryKeys.socialAuth });
      setNotice({ tone: 'success', text: 'Đã cập nhật cấu hình đăng nhập.' });
    } catch (error) {
      setNotice({ tone: 'error', text: errorMessage(error) });
    } finally {
      setPendingAction(null);
    }
  }

  const busy = pendingAction !== null;
  const canReuseSecret = config.hasSecret && clientId.trim() === config.clientId;

  return (
    <Card
      title={<span className="flex items-center gap-2"><SocialProviderLogo provider={config.provider} />{SOCIAL_PROVIDER_LABEL[config.provider]}</span>}
      actions={<Badge tone={config.active ? 'green' : 'gray'}>{config.active ? 'Đã bật' : 'Chưa bật'}</Badge>}
    >
      <form className="space-y-4" onSubmit={(event) => {
        event.preventDefault();
        void update('save', { enabled, clientId, ...(clientSecret ? { clientSecret } : {}) });
        setClientSecret('');
      }}>
        {notice && <Alert tone={notice.tone}>{notice.text}</Alert>}
        <div>
          <a className="text-sm font-semibold text-blue-700 hover:underline" href={consoleLink.href} target="_blank" rel="noreferrer">
            {consoleLink.label}
          </a>
          {config.provider === 'microsoft' && (
            <p className="mt-1 text-sm text-slate-600">Chọn hỗ trợ tài khoản tổ chức và cá nhân, thêm quyền Microsoft Graph User.Read.</p>
          )}
        </div>
        <div className="flex items-end gap-2">
          <div className="min-w-0 flex-1">
            <TextField label="URL chuyển hướng" value={config.redirectUri} readOnly onFocus={(event) => event.currentTarget.select()} />
          </div>
          <Button variant="secondary" onClick={async () => {
            try {
              await navigator.clipboard.writeText(config.redirectUri);
              setCopyMessage('Đã sao chép URL.');
            } catch {
              setCopyMessage('Chọn và sao chép URL trong ô bên cạnh.');
            }
          }}>Sao chép</Button>
        </div>
        {copyMessage && <p role="status" className="text-sm text-slate-600">{copyMessage}</p>}
        <TextField label="Client ID" value={clientId} required={enabled} maxLength={256} disabled={busy}
          onChange={(event) => setClientId(event.target.value)} />
        <TextField label="Client Secret" type="password" autoComplete="new-password" value={clientSecret}
          required={enabled && !canReuseSecret} maxLength={1024} disabled={busy}
          hint={canReuseSecret ? 'Để trống để giữ Client Secret hiện tại.' : 'Dán giá trị Client Secret từ ứng dụng OAuth.'}
          onChange={(event) => setClientSecret(event.target.value)} />
        <label className="flex items-center gap-2 text-sm font-medium text-slate-800">
          <input type="checkbox" checked={enabled} disabled={busy} onChange={(event) => setEnabled(event.target.checked)} />
          Bật đăng nhập
        </label>
        <div className="flex flex-wrap gap-2">
          <Button type="submit" loading={pendingAction === 'save'} disabled={busy}>Lưu cấu hình</Button>
          {config.managed && (
            <Button variant="secondary" disabled={busy} loading={pendingAction === 'reset'} onClick={() => {
              setClientSecret('');
              void update('reset');
            }}>
              Dùng cấu hình .env
            </Button>
          )}
        </div>
      </form>
    </Card>
  );
}
