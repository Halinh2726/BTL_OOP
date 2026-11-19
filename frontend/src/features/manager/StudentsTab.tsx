import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { useDebouncedValue } from '../../shared/hooks/useDebouncedValue';
import {
  formatDateTime, formatPlate, formatPoints, GUARD_STATUS_LABELS, VEHICLE_STATUS_LABELS,
} from '../../shared/utils/format';
import { LedgerTable } from './LedgerTable';

/**
 * Đợt 4a — hồ sơ sinh viên: tìm theo MSSV hoặc họ tên (gõ không dấu cũng được), xem số điểm, xe, lượt gửi và giao dịch
 * gần đây. CHỈ XEM: muốn sửa sai số điểm thì dùng tab "Điều chỉnh" (R10: không sửa giao dịch cũ).
 */
export function StudentsTab() {
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState<string | null>(null);
  const fragment = useDebouncedValue(query.trim());
  const search = useQuery({
    queryKey: queryKeys.studentSearch(fragment),
    queryFn: () => api.searchStudents(fragment),
    enabled: fragment.length >= 2,
  });

  function open(event: FormEvent) {
    event.preventDefault();
    // Enter: nếu chỉ có một kết quả thì mở luôn; không thì coi ô tìm là MSSV
    const only = search.data?.length === 1 ? search.data[0] : undefined;
    if (only) setSelected(only.studentCode);
    else if (query.trim().length >= 2) setSelected(query.trim().toUpperCase());
  }

  return (
    <div className="space-y-6">
      <Card title="Tra cứu sinh viên">
        <form onSubmit={open} className="space-y-3">
          <label className="block space-y-1 text-sm font-medium text-slate-700">
            MSSV hoặc họ tên
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Ví dụ: B21DCCN001 hoặc nguyen van a"
              autoComplete="off"
              className="block w-full rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
          {fragment.length > 0 && fragment.length < 2 && <p className="text-sm text-slate-500">Gõ ít nhất 2 ký tự.</p>}
          {search.isError && <QueryError error={search.error} onRetry={() => void search.refetch()} />}
          {search.data?.length === 0 && <p className="text-sm text-slate-500">Không tìm thấy sinh viên nào.</p>}
          {search.data && search.data.length > 0 && (
            <ul className="divide-y divide-slate-100 rounded-xl ring-1 ring-slate-200">
              {search.data.map((row) => (
                <li key={row.userId}>
                  <button
                    type="button"
                    onClick={() => setSelected(row.studentCode)}
                    aria-current={selected === row.studentCode || undefined}
                    className={`flex w-full flex-wrap items-center justify-between gap-2 px-4 py-2.5 text-left hover:bg-slate-50 ${
                      selected === row.studentCode ? 'bg-blue-50' : ''
                    }`}
                  >
                    <span>
                      <span className="font-semibold">{row.fullName}</span>
                      <span className="ml-2 font-mono text-sm text-slate-500">{row.studentCode}</span>
                    </span>
                    <span className="flex items-center gap-2 text-sm">
                      {row.parkedNow && <Badge tone="blue">Đang gửi xe</Badge>}
                      {row.status === 'BI_KHOA' && <Badge tone="red">Bị khóa</Badge>}
                      <span className="font-semibold">{formatPoints(row.balance)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </form>
      </Card>
      {selected && <StudentProfile code={selected} onClose={() => setSelected(null)} />}
    </div>
  );
}

function StudentProfile({ code, onClose }: { code: string; onClose: () => void }) {
  const profile = useQuery({ queryKey: queryKeys.studentProfile(code), queryFn: () => api.studentProfile(code) });

  if (profile.isPending) return <LoadingBlock />;
  if (profile.isError) return <QueryError error={profile.error} onRetry={() => void profile.refetch()} />;
  const { student, vehicles, recentSessions, recentTransactions } = profile.data;

  return (
    <div className="space-y-6">
      <Card
        title={`${student.fullName} · ${student.studentCode}`}
        actions={<Button size="sm" variant="secondary" onClick={onClose}>Đóng</Button>}
      >
        <div className="grid gap-6 md:grid-cols-2">
          <dl>
            <InfoRow label="Số điểm hiện có" value={<span className="text-lg">{formatPoints(student.balance)}</span>} />
            <InfoRow label="Trạng thái" value={GUARD_STATUS_LABELS[student.status]} />
            <InfoRow label="Email" value={student.email ?? '—'} />
            <InfoRow label="Số điện thoại" value={student.phone ?? '—'} />
            <InfoRow label="Đăng ký lúc" value={formatDateTime(student.createdAt)} />
          </dl>
          <div>
            <h3 className="mb-2 text-sm font-semibold text-slate-700">Xe ({vehicles.length})</h3>
            {vehicles.length === 0 && <p className="text-sm text-slate-500">Chưa đăng ký xe nào.</p>}
            <ul className="space-y-2">
              {vehicles.map((vehicle) => (
                <li key={vehicle.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg bg-slate-50 px-3 py-2">
                  <span>
                    <span className="font-mono font-semibold">{formatPlate(vehicle.plateNumber)}</span>
                    <span className="ml-2 text-sm text-slate-500">{vehicle.vehicleTypeName}</span>
                  </span>
                  <span className="flex gap-1.5">
                    {vehicle.parkedNow && <Badge tone="blue">Trong bãi</Badge>}
                    <Badge tone={vehicle.status === 'DANG_DUNG' ? 'green' : 'gray'}>{VEHICLE_STATUS_LABELS[vehicle.status]}</Badge>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </Card>

      <Card title="Lượt gửi gần đây">
        {recentSessions.length === 0 ? (
          <p className="text-sm text-slate-500">Chưa gửi xe lần nào.</p>
        ) : (
          <div className="-mx-5 overflow-x-auto">
            <table className="min-w-full text-sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
              <thead className="bg-slate-50 text-left text-slate-500">
                <tr>
                  <th className="px-5 py-2 font-medium">Biển số</th>
                  <th className="px-3 py-2 font-medium">Vào</th>
                  <th className="px-3 py-2 font-medium">Ra</th>
                  <th className="px-5 py-2 text-right font-medium">Phí</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {recentSessions.map((session) => (
                  <tr key={session.id}>
                    <td className="px-5 py-2 font-mono">{formatPlate(session.plateNumber)}</td>
                    <td className="px-3 py-2">{formatDateTime(session.enteredAt)}</td>
                    <td className="px-3 py-2">{session.exitedAt ? formatDateTime(session.exitedAt) : <Badge tone="blue">Đang gửi</Badge>}</td>
                    <td className="px-5 py-2 text-right">{session.fee === null ? '—' : formatPoints(session.fee)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Card title="Giao dịch gần đây">
        {recentTransactions.length === 0
          ? <p className="text-sm text-slate-500">Chưa có giao dịch.</p>
          : <LedgerTable items={recentTransactions} compact />}
      </Card>
    </div>
  );
}
