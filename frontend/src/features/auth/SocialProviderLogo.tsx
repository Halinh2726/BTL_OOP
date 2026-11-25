import type { SocialProvider } from '../../shared/types/api';

export function SocialProviderLogo({ provider }: { provider: SocialProvider }) {
  return (
    <img
      src={`/${provider}-sign-in.png`}
      alt=""
      width={20}
      height={20}
      className="h-5 w-5 shrink-0 object-contain"
    />
  );
}
