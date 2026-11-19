import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Badge } from '../../shared/components/Badge';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { ATTENDANCE_LABELS, formatTime, SHIFT_SLOT_LABELS } from '../../shared/utils/format';
import { ATTENDANCE_TONE } from '../manager/attendance';
import { longDate, vietnamToday } from '../owner/revenue/revenueMath';

/** Đợt 4b — lịch trực của bảo vệ: 14 ngày tới và kết quả đối chiếu 7 ngày qua. */
export function MyScheduleCard() {
  const schedule = useQuery({ queryKey: queryKeys.mySchedule, queryFn: api.mySchedule });
  const today = vietnamToday();
  const upcoming = schedule.data?.filter((s) => s.date >= today) ?? [];
  const past = schedule.data?.filter((s) => s.date < today).reverse() ?? [];

  return (
    <Card title="Lịch trực của tôi">
      {schedule.isPending && <LoadingBlock />}
      {schedule.isError && <QueryError error={schedule.error} onRetry={() => void schedule.refetch()} />}
      {schedule.data?.length === 0 && <p className="text-sm text-slate-500">Quản lý chưa xếp ca nào cho bạn.</p>}
      {schedule.data && schedule.data.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2">
          {[{ title: 'Sắp tới', items: upcoming }, { title: '7 ngày qua', items: past }].map((group) => (
            <section key={group.title}>
              <h3 className="mb-2 text-sm font-semibold text-slate-700">{group.title}</h3>
              {group.items.length === 0 && <p className="text-sm text-slate-500">Không có ca nào.</p>}
              <ul className="space-y-1.5">
                {group.items.map((s) => (
                  <li key={s.id} className={`flex items-center justify-between gap-2 rounded-lg px-3 py-2 text-sm ${s.date === today ? 'bg-blue-50' : 'bg-slate-50'}`}>
                    <span>
                      <span className="font-semibold">{longDate(s.date)}</span>
                      <span className="block text-xs text-slate-500">
                        {SHIFT_SLOT_LABELS[s.slot]}{s.actualStart && ` · nhận ca ${formatTime(s.actualStart)}`}
                      </span>
                    </span>
                    <Badge tone={ATTENDANCE_TONE[s.attendance]}>
                      {ATTENDANCE_LABELS[s.attendance]}{s.attendance === 'DI_MUON' ? ` ${s.lateMinutes} phút` : ''}
                    </Badge>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      )}
    </Card>
  );
}
