import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { ConfirmDialog } from '../../shared/components/Dialog';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { Guard } from '../../shared/types/api';
import { GUARD_STATUS_LABELS } from '../../shared/utils/format';
import { CreateGuardDialog } from './CreateGuardDialog';

/** QL03 — danh sách bảo vệ, tạo tài khoản, khóa/mở khóa. Không có xóa (giữ lịch sử ai cho xe vào/ra). */
export function GuardsTab() {
  const queryClient = useQueryClient();
  const guards = useQuery({ queryKey: queryKeys.guards, queryFn: api.guards });
  const [creating, setCreating] = useState(false);
  const [target, setTarget] = useState<Guard | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const changeStatus = useMutation({
    mutationFn: (guard: Guard) => api.changeGuardStatus(guard.id, guard.status === 'HOAT_DONG' ? 'BI_KHOA' : 'HOAT_DONG'),
    onSuccess: async (updated) => {
      setTarget(null);
      setMessage(`Đã ${updated.status === 'BI_KHOA' ? 'khóa' : 'mở khóa'} tài khoản ${updated.username}.`);
      await queryClient.invalidateQueries({ queryKey: queryKeys.guards });
    },
  });

  const locking = target?.status === 'HOAT_DONG';

  return (
    <Card
      title="Tài khoản bảo vệ"
      actions={
        <Button size="sm" onClick={() => setCreating(true)}>
          + Tạo bảo vệ
        </Button>
      }
    >
      {message && (
        <div className="mb-4">
          <Alert tone="success">{message}</Alert>
        </div>
      )}
      {guards.isPending && <LoadingBlock />}
      {guards.isError && <QueryError error={guards.error} onRetry={() => void guards.refetch()} />}
      {guards.data?.length === 0 && <p className="py-6 text-center text-slate-500">Chưa có tài khoản bảo vệ nào.</p>}
      {guards.data && guards.data.length > 0 && (
        <div className="-mx-5 overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-500">
              <tr>
                <th className="px-5 py-2 font-medium">Họ tên</th>
                <th className="px-3 py-2 font-medium">Tên đăng nhập</th>
                <th className="px-3 py-2 font-medium">Điện thoại</th>
                <th className="px-3 py-2 font-medium">Trạng thái</th>
                <th className="px-5 py-2" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {guards.data.map((guard) => (
                <tr key={guard.id}>
                  <td className="px-5 py-3 font-medium">{guard.fullName}</td>
                  <td className="px-3 py-3 font-mono text-slate-600">{guard.username}</td>
                  <td className="px-3 py-3 text-slate-600">{guard.phone}</td>
                  <td className="px-3 py-3">
                    <Badge tone={guard.status === 'HOAT_DONG' ? 'green' : 'red'}>{GUARD_STATUS_LABELS[guard.status]}</Badge>
                  </td>
                  <td className="px-5 py-3 text-right">
                    <Button
                      size="sm"
                      variant={guard.status === 'HOAT_DONG' ? 'danger' : 'secondary'}
                      onClick={() => {
                        changeStatus.reset();
                        setTarget(guard);
                      }}
                    >
                      {guard.status === 'HOAT_DONG' ? 'Khóa' : 'Mở khóa'}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {target && (
        <ConfirmDialog
          title={locking ? 'Khóa tài khoản bảo vệ' : 'Mở khóa tài khoản bảo vệ'}
          confirmLabel={locking ? 'Khóa' : 'Mở khóa'}
          tone={locking ? 'danger' : 'primary'}
          pending={changeStatus.isPending}
          onCancel={() => setTarget(null)}
          onConfirm={() => changeStatus.mutate(target)}
          error={changeStatus.isError && <Alert tone="error">{errorMessage(changeStatus.error)}</Alert>}
        >
          <p>
            {locking ? 'Khóa' : 'Mở khóa'} tài khoản <strong>{target.fullName}</strong> ({target.username})?
          </p>
          {locking && (
            <p className="text-sm text-slate-500">
              Bảo vệ này sẽ bị đăng xuất ngay và không đăng nhập được cho tới khi mở khóa. Lịch sử cho xe vào/ra vẫn được giữ.
            </p>
          )}
        </ConfirmDialog>
      )}

      {creating && (
        <CreateGuardDialog
          onClose={() => setCreating(false)}
          onCreated={(guard) => {
            setCreating(false);
            setMessage(`Đã tạo tài khoản bảo vệ ${guard.username}.`);
          }}
        />
      )}
    </Card>
  );
}
