import { useState } from 'react';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { AuthLayout } from '../auth/AuthLayout';
import { useCurrentUser, useSession } from '../auth/session';
import { ChangePasswordForm } from './ChangePasswordForm';

/**
 * Đăng nhập bằng mật khẩu tạm do quản lý cấp: mọi trang khác bị thay bằng màn hình này cho tới khi đổi mật khẩu
 * (backend cũng chặn mọi API khác bằng PASSWORD_CHANGE_REQUIRED — giao diện chỉ giúp người dùng không bị lạc).
 */
export function ForcedPasswordChangePage() {
  const { user } = useCurrentUser();
  const { retry, signOut } = useSession();
  const [signingOut, setSigningOut] = useState(false);

  return (
    <AuthLayout title="Đặt mật khẩu mới" subtitle={`Xin chào ${user.fullName}`}>
      <div className="space-y-4">
        <Alert tone="warning">
          Bạn đang đăng nhập bằng <strong>mật khẩu tạm</strong> do quản lý cấp. Hãy đặt mật khẩu của riêng bạn để tiếp tục.
        </Alert>
        <ChangePasswordForm currentLabel="Mật khẩu tạm" submitLabel="Đặt mật khẩu và tiếp tục" onChanged={retry} />
        <Button
          variant="ghost"
          className="w-full"
          loading={signingOut}
          onClick={() => {
            setSigningOut(true);
            void signOut();
          }}
        >
          Đăng xuất
        </Button>
      </div>
    </AuthLayout>
  );
}
