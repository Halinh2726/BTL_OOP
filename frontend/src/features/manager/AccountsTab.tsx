import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, errorMessage, isApiError } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { TextField } from '../../shared/components/Field';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { TemporaryPasswordResult } from '../../shared/types/api';
import { AUDIT_ACTION_LABELS, formatDateTime } from '../../shared/utils/format';
import { TemporaryPasswordDialog } from './TemporaryPasswordDialog';

/** Tab "Tài khoản" của quản lý: cấp mật khẩu tạm cho sinh viên quên mật khẩu, xem nhật ký thao tác. */
export function AccountsTab() {
  return (
    <div className="grid items-start gap-6 lg:grid-cols-[2fr_3fr]">
      <StudentPasswordResetCard />
      <AuditLogCard />
    </div>
  );
}

/**
 * Sinh viên quên mật khẩu mà chưa khai báo email: mang thẻ tới quầy. Quản lý tra MSSV, ĐỐI CHIẾU họ tên với thẻ,
 * tick xác nhận rồi mới cấp mật khẩu tạm — giống bước đối chiếu thẻ khi đổi điểm (R08).
 */
function StudentPasswordResetCard() {
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [submitted, setSubmitted] = useState<string | null>(null);
  const [checked, setChecked] = useState(false);
  const [issued, setIssued] = useState<TemporaryPasswordResult | null>(null);

  const student = useQuery({
    queryKey: queryKeys.student(submitted ?? ''),
    queryFn: () => api.lookupStudent(submitted ?? ''),
    enabled: submitted !== null,
    retry: false,
  });

  const reset = useMutation({
    mutationFn: () => api.resetStudentPassword(submitted ?? ''),
    onSuccess: async (result) => {
      setIssued(result);
      setChecked(false);
      await queryClient.invalidateQueries({ queryKey: queryKeys.auditLogs });
    },
  });

  function lookup(event: FormEvent) {
    event.preventDefault();
    const trimmed = code.trim().toUpperCase();
    if (!trimmed) return;
    reset.reset();
    setChecked(false);
    setSubmitted(trimmed);
  }

  return (
    <Card title="Cấp mật khẩu tạm cho sinh viên">
      <form className="flex items-end gap-2" onSubmit={lookup}>
        <div className="flex-1">
          <TextField
            label="MSSV trên thẻ sinh viên"
            className="font-mono uppercase"
            value={code}
            onChange={(event) => setCode(event.target.value)}
          />
        </div>
        <Button type="submit" loading={student.isFetching}>
          Tra cứu
        </Button>
      </form>

      <div className="mt-4 space-y-4">
        {student.isError && (
          isApiError(student.error) && student.error.code === 'STUDENT_NOT_FOUND'
            ? <Alert tone="warning">{student.error.message}</Alert>
            : <QueryError error={student.error} onRetry={() => void student.refetch()} />
        )}
        {student.data && (
          <>
            <dl className="rounded-lg bg-slate-50 px-4 py-2 text-sm">
              <InfoRow label="MSSV" value={<span className="font-mono">{student.data.studentCode}</span>} />
              <InfoRow label="Họ tên" value={student.data.fullName} />
            </dl>
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                className="mt-0.5 h-4 w-4"
                checked={checked}
                onChange={(event) => setChecked(event.target.checked)}
              />
              <span>Tôi đã đối chiếu họ tên, MSSV với thẻ sinh viên của người đang đứng trước quầy.</span>
            </label>
            {reset.isError && <Alert tone="error">{errorMessage(reset.error)}</Alert>}
            <Button variant="danger" disabled={!checked} loading={reset.isPending} onClick={() => reset.mutate()}>
              Cấp mật khẩu tạm
            </Button>
          </>
        )}
      </div>
      {issued && <TemporaryPasswordDialog result={issued} onClose={() => setIssued(null)} />}
    </Card>
  );
}

/** Nhật ký thao tác trên tài khoản (ai, làm gì, với ai, lúc nào) — chỉ xem, không sửa/xóa được. */
function AuditLogCard() {
  const logs = useInfiniteQuery({
    queryKey: queryKeys.auditLogs,
    queryFn: ({ pageParam }) => api.auditLogs({ before: pageParam, limit: 20 }),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (page) => (page.hasMore ? (page.nextBefore ?? undefined) : undefined),
  });
  const items = logs.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <Card title="Nhật ký thao tác tài khoản">
      {logs.isPending && <LoadingBlock />}
      {logs.isError && <QueryError error={logs.error} onRetry={() => void logs.refetch()} />}
      {logs.isSuccess && items.length === 0 && <p className="py-6 text-center text-slate-500">Chưa có thao tác nào.</p>}
      {items.length > 0 && (
        <ul className="divide-y divide-slate-100 text-sm">
          {items.map((item) => (
            <li key={item.id} className="py-3">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <p className="font-semibold text-slate-900">{AUDIT_ACTION_LABELS[item.action]}</p>
                <p className="text-xs text-slate-400">{formatDateTime(item.createdAt)}</p>
              </div>
              <p className="text-slate-600">
                {item.actorName}
                {item.targetName && item.targetUserId !== item.actorId && <> → {item.targetName}</>}
              </p>
              {item.detail && <p className="text-xs text-slate-500">{item.detail}</p>}
            </li>
          ))}
        </ul>
      )}
      {logs.hasNextPage && (
        <div className="mt-3 text-center">
          <Button variant="secondary" size="sm" loading={logs.isFetchingNextPage} onClick={() => void logs.fetchNextPage()}>
            Xem thêm
          </Button>
        </div>
      )}
    </Card>
  );
}
