import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from '../../shared/components/Alert';
import { Card } from '../../shared/components/Card';
import { HOME_BY_ROLE, useCurrentUser } from '../auth/session';
import { ChangePasswordForm } from './ChangePasswordForm';
import { ProfileCard } from './ProfileCard';

/** Trang "Tài khoản" — chung cho cả 4 vai trò: thông tin cá nhân và đổi mật khẩu. */
export function AccountPage() {
  const { user } = useCurrentUser();
  const [changed, setChanged] = useState(false);

  return (
    <div className="space-y-6">
      <Link to={HOME_BY_ROLE[user.role]} className="text-sm font-semibold text-blue-700 hover:underline">
        ← Về trang chính
      </Link>
      <div className="grid items-start gap-6 md:grid-cols-2">
        <ProfileCard />
        <Card title="Đổi mật khẩu">
          <div className="space-y-4">
            {changed && (
              <Alert tone="success">
                Đã đổi mật khẩu. Các thiết bị khác đang đăng nhập tài khoản này đã bị đăng xuất.
              </Alert>
            )}
            <ChangePasswordForm onChanged={() => setChanged(true)} />
          </div>
        </Card>
      </div>
    </div>
  );
}
