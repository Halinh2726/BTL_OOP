import { Button } from '../../shared/components/Button';
import { Logo } from '../../shared/components/Logo';
import type { SocialProvider } from '../../shared/types/api';
import { SOCIAL_PROVIDER_LABEL, socialStartUrl, useSocialAuthStatus } from './socialAuth';
import { SocialProviderLogo } from './SocialProviderLogo';

const PROVIDERS: SocialProvider[] = ['google', 'microsoft'];

export function SocialSignInOptions() {
  const { data } = useSocialAuthStatus();
  const providers = data?.providers ?? [];
  const activeProviders = PROVIDERS.filter((provider) => providers.includes(provider));
  const ptitActive = providers.includes('microsoft-ptit');

  if (activeProviders.length === 0 && !ptitActive) return null;

  return (
    <div className="mt-5 space-y-2 border-t border-slate-200 pt-4">
      {activeProviders.map((provider) => (
        <Button
          key={provider}
          variant="secondary"
          className="w-full"
          onClick={() => window.location.assign(socialStartUrl(provider))}
        >
          <SocialProviderLogo provider={provider} />
          Tiếp tục với {SOCIAL_PROVIDER_LABEL[provider]}
        </Button>
      ))}
      {ptitActive && (
        <Button
          variant="secondary"
          className="w-full"
          onClick={() => window.location.assign(socialStartUrl('microsoft-ptit'))}
        >
          <span aria-hidden="true"><Logo className="h-5 w-5" /></span>
          Đăng nhập bằng email PTIT
        </Button>
      )}
    </div>
  );
}
