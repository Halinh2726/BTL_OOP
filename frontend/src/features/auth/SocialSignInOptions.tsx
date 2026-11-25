import { Button } from '../../shared/components/Button';
import { Logo } from '../../shared/components/Logo';
import type { SocialProvider } from '../../shared/types/api';
import { SOCIAL_PROVIDER_LABEL, socialStartUrl, useSocialAuthStatus } from './socialAuth';
import { SocialProviderLogo } from './SocialProviderLogo';

const PROVIDERS: SocialProvider[] = ['google', 'microsoft'];

export function SocialSignInOptions() {
  const { data, isError } = useSocialAuthStatus();
  const unconfigured = data && PROVIDERS.filter((provider) => !data.providers.includes(provider));

  return (
    <div className="mt-5 space-y-2 border-t border-slate-200 pt-4">
      {PROVIDERS.map((provider) => (
        <Button
          key={provider}
          variant="secondary"
          className="w-full disabled:!opacity-100 disabled:text-slate-500 disabled:hover:bg-white"
          disabled={!data?.providers.includes(provider)}
          onClick={() => window.location.assign(socialStartUrl(provider))}
        >
          <SocialProviderLogo provider={provider} />
          Tiếp tục với {SOCIAL_PROVIDER_LABEL[provider]}
        </Button>
      ))}
      {data?.providers.includes('microsoft') && (
        <Button
          variant="secondary"
          className="w-full"
          onClick={() => window.location.assign(socialStartUrl('microsoft-ptit'))}
        >
          <span aria-hidden="true"><Logo className="h-5 w-5" /></span>
          Đăng nhập bằng email PTIT
        </Button>
      )}
      {unconfigured?.length ? (
        <p className="text-center text-sm text-slate-600">
          Chưa cấu hình đăng nhập qua {unconfigured.map((provider) => SOCIAL_PROVIDER_LABEL[provider]).join(' và ')}.
        </p>
      ) : isError && (
        <p className="text-center text-sm text-slate-600">Chưa kiểm tra được tình trạng đăng nhập qua Google và Microsoft.</p>
      )}
    </div>
  );
}
