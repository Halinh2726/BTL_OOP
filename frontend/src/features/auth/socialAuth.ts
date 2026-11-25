import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import type { SocialProvider } from '../../shared/types/api';

export const SOCIAL_PROVIDER_LABEL: Record<SocialProvider, string> = {
  google: 'Google (Gmail)',
  microsoft: 'Microsoft (Outlook/Hotmail)',
};

export function useSocialAuthStatus() {
  return useQuery({ queryKey: queryKeys.socialAuth, queryFn: api.socialAuthStatus });
}

export function socialStartUrl(provider: SocialProvider | 'microsoft-ptit') {
  return `/api/v1/auth/social/start/${provider}`;
}

export function socialErrorMessage(status: string | null) {
  if (status === 'locked') return 'Tài khoản đã bị khóa. Vui lòng liên hệ quản lý.';
  if (status === 'denied') return 'Đăng nhập chưa được cấp quyền. Nếu Microsoft yêu cầu quản trị viên phê duyệt, hãy liên hệ bộ phận IT của trường.';
  if (status === 'failed') return 'Không xác thực được tài khoản Google hoặc Microsoft. Vui lòng thử lại.';
  return null;
}
