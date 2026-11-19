import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { PlannedShift, ShiftSlot } from '../../shared/types/api';
import { ATTENDANCE_LABELS, SHIFT_SLOT_LABELS } from '../../shared/utils/format';
import { eachDay, longDate, shortDate, vietnamToday } from '../owner/revenue/revenueMath';
import { ATTENDANCE_TONE, addDays } from './attendance';

const SLOTS = Object.keys(SHIFT_SLOT_LABELS) as ShiftSlot[];

/** Một bảo vệ trong ô lịch: tên + kết quả đối chiếu; gỡ được khi ca chưa bắt đầu. */
function ShiftChip({ shift, onRemove, removing }: { shift: PlannedShift; onRemove: () => void; removing: boolean }) {
  return (
    <li className="flex items-center justify-between gap-1 rounded-lg bg-white px-2 py-1 text-xs shadow-sm ring-1 ring-slate-200">
      <span className="min-w-0">
        <span className="block truncate font-semibold text-slate-800">{shift.guardName}</span>
        <Badge tone={ATTENDANCE_TONE[shift.attendance]}>
          {ATTENDANCE_LABELS[shift.attendance]}{shift.attendance === 'DI_MUON' ? ` ${shift.lateMinutes}′` : ''}
        </Badge>
      </span>
      {shift.attendance === 'SAP_TOI' && (
        <button
          type="button"
          onClick={onRemove}
          disabled={removing}
          aria-label={`Gỡ ${shift.guardName ?? 'bảo vệ'} khỏi ca`}
          className="rounded px-1 text-base leading-none text-slate-400 hover:bg-red-50 hover:text-red-700 disabled:opacity-50"
        >
          ×
        </button>
      )}
    </li>
  );
}

/**
 * Đợt 4b — phân ca bảo vệ theo tuần (3 ca × 7 ngày). Mỗi ca hiện kết quả đối chiếu với giờ bảo vệ bấm "Nhận ca":
 * đúng giờ, đi muộn (số phút), vắng. Chỉ gỡ được ca chưa bắt đầu.
 */
export function ShiftPlanTab() {
  const queryClient = useQueryClient();
  const today = vietnamToday();
  const [from, setFrom] = useState(today);
  const [date, setDate] = useState(today);
  const [slot, setSlot] = useState<ShiftSlot>('SANG');
  const [guardId, setGuardId] = useState('');
  const week = useQuery({ queryKey: queryKeys.shiftWeek(from), queryFn: () => api.shiftWeek(from), placeholderData: keepPreviousData });
  const days = week.data ? eachDay(week.data.from, week.data.to) : [];
  const activeGuards = week.data?.guards.filter((g) => g.active) ?? [];

  const refresh = () => queryClient.invalidateQueries({ queryKey: queryKeys.shiftWeekAll });
  const assign = useMutation({
    mutationFn: () => api.assignShift({ date, slot, guardId: Number(guardId) }),
    onSuccess: refresh,
  });
  const remove = useMutation({ mutationFn: (id: number) => api.removeShift(id), onSuccess: refresh });

  function onAssign(event: FormEvent) {
    event.preventDefault();
    if (guardId) assign.mutate();
  }

  return (
    <Card
      title="Phân ca bảo vệ"
      actions={
        <div className="flex gap-1.5">
          <Button size="sm" variant="secondary" onClick={() => setFrom(addDays(from, -7))}>← Tuần trước</Button>
          <Button size="sm" variant="secondary" disabled={from === today} onClick={() => setFrom(today)}>Từ hôm nay</Button>
          <Button size="sm" variant="secondary" onClick={() => setFrom(addDays(from, 7))}>Tuần sau →</Button>
        </div>
      }
    >
      <div className="space-y-4">
        <form onSubmit={onAssign} className="flex flex-wrap items-end gap-3 rounded-xl bg-slate-50 p-3">
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Ngày
            <select value={date} onChange={(e) => setDate(e.target.value)} className="block rounded-lg border border-slate-300 bg-white px-3 py-1.5">
              {(days.length ? days : [today]).map((d) => <option key={d} value={d}>{longDate(d)}</option>)}
            </select>
          </label>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Ca
            <select value={slot} onChange={(e) => setSlot(e.target.value as ShiftSlot)} className="block rounded-lg border border-slate-300 bg-white px-3 py-1.5">
              {SLOTS.map((s) => <option key={s} value={s}>{SHIFT_SLOT_LABELS[s]}</option>)}
            </select>
          </label>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Bảo vệ
            <select value={guardId} onChange={(e) => setGuardId(e.target.value)} className="block rounded-lg border border-slate-300 bg-white px-3 py-1.5">
              <option value="">— Chọn —</option>
              {activeGuards.map((g) => <option key={g.id} value={g.id}>{g.fullName}</option>)}
            </select>
          </label>
          <Button type="submit" size="sm" disabled={!guardId} loading={assign.isPending}>Xếp ca</Button>
        </form>
        {assign.isError && <Alert tone="error">{errorMessage(assign.error)}</Alert>}
        {remove.isError && <Alert tone="error">{errorMessage(remove.error)}</Alert>}
        {week.isPending && <LoadingBlock />}
        {week.isError && <QueryError error={week.error} onRetry={() => void week.refetch()} />}
        {week.data && (
          <div className={`-mx-5 overflow-x-auto transition-opacity ${week.isPlaceholderData ? 'opacity-50' : ''}`}>
            <table className="min-w-[56rem] table-fixed border-separate border-spacing-1 px-4 text-sm">
              <thead>
                <tr>
                  <th className="w-28 text-left text-xs font-medium text-slate-500">Ca</th>
                  {days.map((d) => (
                    <th key={d} className={`text-left text-xs font-semibold ${d === today ? 'text-blue-700' : 'text-slate-600'}`}>
                      {longDate(d).split(',')[0]} {shortDate(d)}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {SLOTS.map((s) => (
                  <tr key={s}>
                    <th className="align-top text-left text-xs font-medium text-slate-600">{SHIFT_SLOT_LABELS[s].replace('Ca ', '')}</th>
                    {days.map((d) => {
                      const cell = week.data.shifts.filter((shift) => shift.date === d && shift.slot === s);
                      return (
                        <td key={d} className={`h-16 align-top rounded-lg p-1.5 ${d === today ? 'bg-blue-50' : 'bg-slate-50'}`}>
                          {cell.length === 0 ? (
                            <span className="text-xs text-red-600">Chưa có người</span>
                          ) : (
                            <ul className="space-y-1">
                              {cell.map((shift) => (
                                <ShiftChip
                                  key={shift.id}
                                  shift={shift}
                                  removing={remove.isPending && remove.variables === shift.id}
                                  onRemove={() => remove.mutate(shift.id)}
                                />
                              ))}
                            </ul>
                          )}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="text-xs text-slate-500">
          Đối chiếu tự động với giờ bảo vệ bấm "Nhận ca": muộn quá 15 phút là đi muộn, hết ca không nhận là vắng.
        </p>
      </div>
    </Card>
  );
}
