import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { SOCIAL_PROVIDER_LABEL, socialErrorMessage, socialStartUrl, useSocialAuthStatus } from '../auth/socialAuth';

export function SocialAccountsCard() {
  const status = useSocialAuthStatus();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const result = params.get('social');
  const pending = result === 'confirm' ? status.data?.pending : null;
  const link = useMutation({
    mutationFn: api.linkSocialAccount,
    onSuccess: () => {
      setParams({}, { replace: true });
      void queryClient.invalidateQueries({ queryKey: queryKeys.socialAuth });
    },
  });
  const providerError = socialErrorMessage(result);
  if (!status.data?.providers.length && !result && !link.isSuccess) return null;

  return (
    <Card title="Đăng nhập bằng Google hoặc Microsoft">
      <div className="space-y-3">
        {providerError && <Alert tone="error">{providerError}</Alert>}
        {status.isError && <Alert tone="error">{errorMessage(status.error)}</Alert>}
        {link.isSuccess && <Alert tone="success">Đã liên kết tài khoản. Bạn có thể dùng tài khoản này để đăng nhập.</Alert>}
        {link.isError && <Alert tone="error">{errorMessage(link.error)}</Alert>}
        {result === 'confirm' && status.isSuccess && !pending && (
          <Alert tone="warning">Phiên xác thực đã hết hạn. Vui lòng chọn lại nhà cung cấp.</Alert>
        )}
        {pending && (
          <div className="space-y-3 rounded-lg bg-blue-50 p-3">
            <p className="break-words text-sm text-slate-800">
              Liên kết {SOCIAL_PROVIDER_LABEL[pending.provider]}{pending.label && ` (${pending.label})`} với tài khoản gửi xe này?
            </p>
            <div className="flex flex-wrap gap-2">
              <Button onClick={() => link.mutate(pending.id)} loading={link.isPending}>Xác nhận liên kết</Button>
              <Button
                variant="secondary"
                disabled={link.isPending}
                onClick={() => {
                  setParams({}, { replace: true });
                  link.reset();
                }}
              >
                Hủy
              </Button>
            </div>
          </div>
        )}
        <ul className="divide-y divide-slate-100">
          {status.data?.providers.includes('google') && (
            <li className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
              <span className="font-medium text-slate-900">{SOCIAL_PROVIDER_LABEL.google}</span>
              {status.data.linked.includes('google') ? (
                <span className="text-emerald-700">Đã liên kết</span>
              ) : (
                <a href={socialStartUrl('google')} className="font-semibold text-blue-700 hover:underline">Liên kết</a>
              )}
            </li>
          )}
          {(status.data?.providers.includes('microsoft') || status.data?.providers.includes('microsoft-ptit')) && (
            <li className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
              <span className="font-medium text-slate-900">{SOCIAL_PROVIDER_LABEL.microsoft}</span>
              {status.data.linked.includes('microsoft') ? (
                <span className="text-emerald-700">Đã liên kết</span>
              ) : (
                <span className="flex flex-wrap gap-x-3 gap-y-1 font-semibold text-blue-700">
                  {status.data.providers.includes('microsoft') && (
                    <a href={socialStartUrl('microsoft')} className="hover:underline">Liên kết</a>
                  )}
                  {status.data.providers.includes('microsoft-ptit') && (
                    <a href={socialStartUrl('microsoft-ptit')} className="hover:underline">Liên kết email PTIT</a>
                  )}
                </span>
              )}
            </li>
          )}
        </ul>
      </div>
    </Card>
  );
}
