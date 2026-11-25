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
import type { IncidentItem, ShiftSummary, ShiftView } from '../../shared/types/api';
import { INCIDENT_TYPE_LABELS, formatCash, formatDateTime, formatDuration, formatNumber, formatTime } from '../../shared/utils/format';
import { ActivityColumns } from './ActivityColumns';
import { CashDifference } from './CashDifference';
import { CashField, readCash } from './CashField';
import { MyScheduleCard } from './MyScheduleCard';
import { ShiftHistoryCard } from './ShiftHistoryCard';

/**
 * Ca trực của bảo vệ: nhận ca (xác nhận biên bản ca trước + khai tiền mặt đang cầm), số liệu ca đang trực,
 * kết thúc ca (khai tiền bàn giao), lịch sử xe vào / xe ra (2 cột, có tìm kiếm), lịch sử trực ca (chấm công).
 * Số liệu lượt xe do hệ thống tự đếm; tiền mặt là số bảo vệ tự khai — không có doanh thu hay điểm (R06).
 */
export function ShiftTab() {
  const overview = useQuery({ queryKey: queryKeys.currentShift, queryFn: api.currentShift, refetchInterval: 30_000 });

  if (overview.isPending) return <LoadingBlock />;
  if (overview.isError) return <QueryError error={overview.error} onRetry={() => void overview.refetch()} />;

  const { current, liveSummary, pendingHandovers, openIncidents } = overview.data;
  return (
    <div className="space-y-6">
      {current && liveSummary ? (
        <CurrentShiftCard shift={current} summary={liveSummary} />
      ) : (
        <StartShiftCard pending={pendingHandovers} openIncidents={openIncidents} />
      )}
      <GuardCashSheetCard />
      <MyScheduleCard />
      <MyActivityCard />
      <ShiftHistoryCard />
    </div>
  );
}

function SummaryGrid({ summary }: { summary: ShiftSummary }) {
  const cells = [
    ['Lượt cho vào', summary.checkIns],
    ['Lượt cho ra', summary.checkOuts],
    ['Xe trong bãi', summary.vehiclesInLot],
    ['Sự cố chưa xử lý', summary.openIncidents],
  ] as const;
  return (
    <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
      {cells.map(([label, value]) => (
        <div key={label} className="rounded-xl bg-slate-50 p-3 text-center">
          <dt className="text-xs text-slate-500">{label}</dt>
          <dd className={`text-3xl font-bold ${label === 'Sự cố chưa xử lý' && value > 0 ? 'text-red-700' : 'text-slate-900'}`}>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function StartShiftCard({ pending, openIncidents }: { pending: ShiftView[]; openIncidents: IncidentItem[] }) {
  const queryClient = useQueryClient();
  const [read, setRead] = useState(false);
  const handedOver = pending.reduce((sum, shift) => sum + (shift.closingCash ?? 0), 0);
  // Có biên bản thì điền sẵn đúng số tiền ca trước bàn giao; bảo vệ đếm lại, lệch thì sửa
  const [cash, setCash] = useState(pending.length > 0 ? formatNumber(handedOver) : '');
  const openingCash = readCash(cash);
  const cashOk = openingCash !== null && !Number.isNaN(openingCash);

  const start = useMutation({
    mutationFn: () => api.startShift(pending.map((shift) => shift.id), openingCash ?? 0),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['guard'] }),
    onError: () => void queryClient.invalidateQueries({ queryKey: queryKeys.currentShift }),
  });

  return (
    <Card title="Nhận ca">
      <div className="space-y-4">
        {pending.length === 0 ? (
          <p className="text-slate-600">Không có biên bản bàn giao nào đang chờ. Khai số tiền mặt đang cầm rồi bấm "Nhận ca".</p>
        ) : (
          <>
            <Alert tone="warning">Đọc biên bản bàn giao của ca trước, đếm lại tiền mặt rồi xác nhận nhận ca.</Alert>
            {pending.map((shift) => (
              <div key={shift.id} className="space-y-3 rounded-xl p-4 ring-1 ring-slate-200">
                <p className="font-semibold">
                  Ca của {shift.guardName ?? 'bảo vệ'} · {formatTime(shift.startedAt)}–{shift.endedAt ? formatTime(shift.endedAt) : ''}
                  <span className="ml-2 text-sm font-normal text-slate-500">{shift.endedAt && formatDateTime(shift.endedAt)}</span>
                </p>
                {shift.summary && <SummaryGrid summary={shift.summary} />}
                <p className="rounded-lg bg-blue-50 px-3 py-2 text-blue-900">
                  Tiền mặt bàn giao: <strong>{formatCash(shift.closingCash ?? 0)}</strong>
                </p>
                {shift.handoverNote && <p className="rounded-lg bg-amber-50 px-3 py-2 text-amber-900">Ghi chú: {shift.handoverNote}</p>}
              </div>
            ))}
            {openIncidents.length > 0 && (
              <div>
                <p className="mb-1 text-sm font-semibold text-slate-700">Sự cố chưa xử lý</p>
                <ul className="space-y-1 text-sm">
                  {openIncidents.map((incident) => (
                    <li key={incident.id}>
                      • {INCIDENT_TYPE_LABELS[incident.type]}
                      {incident.plateDisplay && <span className="font-mono"> {incident.plateDisplay}</span>}: {incident.description}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            <label className="flex items-start gap-2">
              <input type="checkbox" className="mt-1 h-5 w-5" checked={read} onChange={(event) => setRead(event.target.checked)} />
              <span>Tôi đã đọc biên bản bàn giao và các sự cố chưa xử lý.</span>
            </label>
          </>
        )}
        <CashField
          label="Tiền mặt đang cầm khi nhận ca"
          value={cash}
          onChange={setCash}
          hint={
            pending.length > 0
              ? `Ca trước bàn giao ${formatCash(handedOver)}. Đếm lại — số khác sẽ được ghi là chênh lệch để quản lý đối chiếu.`
              : 'Tiền lẻ, tiền thu hộ... Không cầm tiền thì nhập 0.'
          }
        />
        {cashOk && pending.length > 0 && openingCash !== handedOver && (
          <Alert tone="warning">
            Số tiền khai {openingCash > handedOver ? 'nhiều' : 'ít'} hơn ca trước bàn giao {formatCash(Math.abs(openingCash - handedOver))}.
          </Alert>
        )}
        {start.isError && <Alert tone="error">{errorMessage(start.error)}</Alert>}
        <Button
          size="xl"
          className="w-full"
          disabled={(pending.length > 0 && !read) || !cashOk}
          loading={start.isPending}
          onClick={() => start.mutate()}
        >
          Nhận ca
        </Button>
      </div>
    </Card>
  );
}

function CurrentShiftCard({ shift, summary }: { shift: ShiftView; summary: ShiftSummary }) {
  const queryClient = useQueryClient();
  const [ending, setEnding] = useState(false);
  const [note, setNote] = useState('');
  const [cash, setCash] = useState('');
  const closingCash = readCash(cash);
  const cashOk = closingCash !== null && !Number.isNaN(closingCash);
  const cashSheet = useQuery({ queryKey: queryKeys.guardCashSheet, queryFn: api.guardCashSheet });
  const expectedHandover = cashSheet.data ? cashSheet.data.expectedClosingCash : shift.openingCash;
  const end = useMutation({
    mutationFn: () => api.endShift(note, closingCash ?? 0),
    onSuccess: async () => {
      setEnding(false);
      setNote('');
      setCash('');
      await queryClient.invalidateQueries({ queryKey: ['guard'] });
    },
  });

  return (
    <Card
      title={
        <>
          Ca đang trực <Badge tone="green">Từ {formatTime(shift.startedAt)}</Badge>{' '}
          <span className="text-sm font-normal text-slate-500">· {formatDuration(shift.workedMinutes)}</span>
        </>
      }
      actions={
        <Button
          variant="secondary"
          onClick={() => {
            // Lấy số liệu mới nhất để bảo vệ xem trước; backend vẫn tự đếm lại lúc lưu biên bản
            void queryClient.invalidateQueries({ queryKey: queryKeys.currentShift });
            void queryClient.invalidateQueries({ queryKey: queryKeys.guardCashSheet });
            setEnding(true);
          }}
        >
          Kết thúc ca &amp; bàn giao
        </Button>
      }
    >
      <div className="space-y-3">
        <SummaryGrid summary={summary} />
        <p className="text-sm text-slate-600">
          Tiền mặt khai lúc nhận ca: <strong>{formatCash(shift.openingCash)}</strong>
          <CashDifference difference={shift.openingDiscrepancy} />
          {cashSheet.data && cashSheet.data.totalCollected > 0 && (
            <>
              {' '}
              · Đã thu tại chốt: <strong>{formatCash(cashSheet.data.totalCollected)}</strong>
            </>
          )}
        </p>
      </div>
      {ending && (
        <ConfirmDialog
          title="Kết thúc ca và lập biên bản bàn giao"
          confirmLabel="Kết thúc ca"
          pending={end.isPending}
          onCancel={() => setEnding(false)}
          onConfirm={() => cashOk && end.mutate()}
          error={
            (end.isError && <Alert tone="error">{errorMessage(end.error)}</Alert>) ||
            (!cashOk && <p className="text-sm text-red-600">Nhập số tiền mặt bàn giao (không có thì nhập 0).</p>)
          }
        >
          <p className="text-sm text-slate-600">Hệ thống tự ghi số liệu dưới đây vào biên bản; bảo vệ ca sau phải xác nhận khi nhận ca.</p>
          <SummaryGrid summary={summary} />
          <div className="rounded-lg bg-slate-50 p-3 text-sm space-y-1">
            <div className="flex justify-between">
              <span className="text-slate-600">Tiền mặt nhận đầu ca:</span>
              <span className="font-semibold">{formatCash(shift.openingCash)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-600">Tiền mặt thu tại chốt trong ca:</span>
              <span className="font-semibold">{formatCash(cashSheet.data?.totalCollected ?? 0)}</span>
            </div>
            <div className="flex justify-between border-t border-slate-200 pt-1 font-semibold text-blue-900">
              <span>Dự kiến bàn giao (= nhận ca + thu tại chốt):</span>
              <span>{formatCash(expectedHandover)}</span>
            </div>
          </div>
          <CashField
            label="Tiền mặt bàn giao cho ca sau"
            value={cash}
            onChange={setCash}
            hint={`Nhận ca ${formatCash(shift.openingCash)} + thu tại chốt ${formatCash(cashSheet.data?.totalCollected ?? 0)} = ${formatCash(expectedHandover)}.`}
          />
          {cashOk && cashSheet.data && closingCash !== expectedHandover && (
            <Alert tone="warning">
              Số tiền bàn giao {closingCash > expectedHandover ? 'nhiều' : 'ít'} hơn dự kiến{' '}
              {formatCash(Math.abs(closingCash - expectedHandover))}.
            </Alert>
          )}
          <div className="space-y-1">
            <label htmlFor="handover-note" className="block text-sm font-medium text-slate-700">
              Ghi chú cho ca sau (không bắt buộc)
            </label>
            <textarea
              id="handover-note"
              rows={3}
              maxLength={500}
              value={note}
              onChange={(event) => setNote(event.target.value)}
              placeholder="Ví dụ: đèn cổng sau hỏng, xe 29A-123.45 chủ xe báo sẽ lấy muộn"
              className="block w-full rounded-lg border border-slate-300 px-3 py-2 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
          </div>
        </ConfirmDialog>
      )}
    </Card>
  );
}

/** Mục 3.6 PDF — bảng kê tiền mặt đã thu tại chốt trong ca trực của bảo vệ đang đăng nhập. */
function GuardCashSheetCard() {
  const sheet = useQuery({ queryKey: queryKeys.guardCashSheet, queryFn: api.guardCashSheet, refetchInterval: 30_000 });

  return (
    <Card
      title={
        <>
          Bảng kê tiền mặt tại chốt{' '}
          {sheet.data && (
            <span className="text-sm font-normal text-slate-500">
              {sheet.data.inShift ? 'trong ca hiện tại' : '12 giờ gần nhất'}
            </span>
          )}
        </>
      }
    >
      {sheet.isPending && <LoadingBlock />}
      {sheet.isError && <QueryError error={sheet.error} onRetry={() => void sheet.refetch()} />}
      {sheet.data && (
        <div className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-3" style={{ fontVariantNumeric: 'tabular-nums' }}>
            <div className="rounded-xl bg-slate-50 p-3">
              <p className="text-xs text-slate-500">Tiền nhận ca</p>
              <p className="text-lg font-bold text-slate-900">{formatCash(sheet.data.openingCash)}</p>
            </div>
            <div className="rounded-xl bg-amber-50 p-3 ring-1 ring-amber-200">
              <p className="text-xs text-amber-800">+ Thu tiền mặt tại chốt ({sheet.data.entries.length} lượt)</p>
              <p className="text-lg font-bold text-amber-900">{formatCash(sheet.data.totalCollected)}</p>
              <p className="text-xs text-amber-800">
                Tiền mặt từ khách {formatCash(sheet.data.entries.filter((entry) => entry.guest)
                  .reduce((total, entry) => total + entry.amount, 0))}
              </p>
            </div>
            <div className="rounded-xl bg-blue-50 p-3 ring-1 ring-blue-200">
              <p className="text-xs text-blue-800">= Dự kiến bàn giao</p>
              <p className="text-lg font-bold text-blue-900">{formatCash(sheet.data.expectedClosingCash)}</p>
            </div>
          </div>

          {sheet.data.entries.length === 0 ? (
            <p className="py-3 text-center text-sm text-slate-500">Chưa thu tiền mặt lượt nào trong ca này.</p>
          ) : (
            <div className="-mx-5 overflow-x-auto">
              <table className="min-w-full text-sm" style={{ fontVariantNumeric: 'tabular-nums' }}>
                <thead className="bg-slate-50 text-left text-slate-500">
                  <tr>
                    <th className="px-5 py-2 font-medium">Giờ thu</th>
                    <th className="px-3 py-2 font-medium">Biển số</th>
                    <th className="px-3 py-2 font-medium">Người gửi / Mã vé</th>
                    <th className="px-3 py-2 font-medium">Loại phí</th>
                    <th className="px-5 py-2 text-right font-medium">Số tiền</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {sheet.data.entries.map((entry) => (
                    <tr key={`${Boolean(entry.guest)}-${entry.collectionId}`}>
                      <td className="whitespace-nowrap px-5 py-2 text-slate-600">{formatTime(entry.collectedAt)}</td>
                      <td className="whitespace-nowrap px-3 py-2 font-mono font-semibold">{entry.plateDisplay}</td>
                      <td className="px-3 py-2">
                        {entry.fullName} <span className="text-xs text-slate-500">({entry.studentCode})</span>
                      </td>
                      <td className="px-3 py-2">
                        {entry.overnight ? <Badge tone="amber">Qua đêm</Badge> : <Badge tone="gray">Thường</Badge>}
                      </td>
                      <td className="whitespace-nowrap px-5 py-2 text-right font-semibold text-slate-900">
                        {formatCash(entry.amount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </Card>
  );
}

/** Các xe CHÍNH mình đã cho vào / cho ra — chia 2 cột, mỗi cột tìm được biển số — để đối chiếu khi sinh viên thắc mắc. */
function MyActivityCard() {
  const activity = useQuery({ queryKey: queryKeys.myGateActivity, queryFn: api.myGateActivity, refetchInterval: 30_000 });

  return (
    <Card
      title={
        <>
          Lịch sử xe vào / xe ra{' '}
          {activity.data && (
            <span className="text-sm font-normal text-slate-500">
              {activity.data.inShift ? 'từ lúc nhận ca' : '12 giờ gần nhất'}
            </span>
          )}
        </>
      }
    >
      {activity.isPending && <LoadingBlock />}
      {activity.isError && <QueryError error={activity.error} onRetry={() => void activity.refetch()} />}
      {activity.data && <ActivityColumns items={activity.data.items} />}
    </Card>
  );
}
