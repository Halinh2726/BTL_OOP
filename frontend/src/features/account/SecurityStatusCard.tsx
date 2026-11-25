import { useQuery } from '@tanstack/react-query';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Card } from '../../shared/components/Card';
import { AUDIT_ACTION_LABELS, formatDateTime } from '../../shared/utils/format';

/** Hiển thị trạng thái bảo mật, căn cứ đổi/khôi phục mật khẩu và lịch sử bảo mật cá nhân. */
export function SecurityStatusCard() {
  const query = useQuery({
    queryKey: queryKeys.mySecurityActivity,
    queryFn: api.mySecurityActivity,
  });

  return (
    <Card title="An toàn tài khoản & Căn cứ xác thực mật khẩu">
      {query.isPending && <p className="text-sm text-slate-500">Đang tải thông tin bảo mật…</p>}
      {query.isError && <Alert tone="error">{errorMessage(query.error)}</Alert>}
      {query.data && (
        <div className="space-y-4 text-sm">
          <div className="flex flex-wrap gap-2">
            <Badge tone={query.data.hasEmail ? 'green' : 'amber'}>
              {query.data.hasEmail ? `Email khôi phục: ${query.data.maskedEmail}` : 'Chưa có email khôi phục'}
            </Badge>
            <Badge tone="gray">Phiên tối đa {query.data.sessionMaxHours} giờ</Badge>
            <Badge tone={query.data.mustChangePassword ? 'red' : 'blue'}>
              {query.data.mustChangePassword ? 'Đang dùng mật khẩu tạm' : 'Mật khẩu hoạt động bình thường'}
            </Badge>
            {query.data.passwordChangedAt && (
              <Badge tone="gray">Đổi mật khẩu gần nhất: {formatDateTime(query.data.passwordChangedAt)}</Badge>
            )}
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-1">
              <p className="font-semibold text-slate-900">Khi tự đổi mật khẩu (đang đăng nhập)</p>
              <p className="text-xs leading-relaxed text-slate-600">{query.data.changePasswordBasis}</p>
            </div>
            <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 space-y-1">
              <p className="font-semibold text-slate-900">Khi quên mật khẩu</p>
              <p className="text-xs leading-relaxed text-slate-600">{query.data.forgotPasswordBasis}</p>
            </div>
          </div>

          <div>
            <h3 className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
              Hoạt động bảo mật & tài khoản gần đây
            </h3>
            {query.data.recentActivities.length === 0 ? (
              <p className="text-xs text-slate-500">Chưa có sự kiện thay đổi mật khẩu hoặc hồ sơ nào được ghi nhận.</p>
            ) : (
              <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                {query.data.recentActivities.map((item) => (
                  <li key={item.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-xs">
                    <div>
                      <span className="font-semibold text-slate-900">
                        {AUDIT_ACTION_LABELS[item.action] ?? item.action}
                      </span>
                      {item.detail && <span className="text-slate-600"> — {item.detail}</span>}
                    </div>
                    <span className="text-slate-500">{formatDateTime(item.createdAt)}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}
