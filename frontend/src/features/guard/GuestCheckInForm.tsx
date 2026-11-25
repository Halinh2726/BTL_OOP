import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage, guestApi, isApiError } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import type { GuestEntry } from '../../shared/types/guest';
import { formatCash, formatDuration } from '../../shared/utils/format';
import { GuestTicketCard } from './GuestTicketCard';

const INPUT = 'block w-full rounded-lg border border-slate-300 bg-white px-3 py-2';

export function GuestCheckInForm({ initialPlate, onStudent }: { initialPlate: string; onStudent: () => void }) {
  const [plate, setPlate] = useState(initialPlate);
  const [type, setType] = useState('XE_MAY');
  const [unplated, setUnplated] = useState(false);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [note, setNote] = useState('');
  const types = useQuery({ queryKey: queryKeys.vehicleTypes, queryFn: api.vehicleTypes });
  const client = useQueryClient();
  const preview = useMutation({ mutationFn: guestApi.previewEntry });
  const checkIn = useMutation({
    mutationFn: guestApi.checkIn,
    onSuccess: () => { void client.invalidateQueries({ queryKey: ['guard'] }); },
  });

  function resetPreview() {
    preview.reset();
    checkIn.reset();
  }

  if (checkIn.data) {
    return <GuestTicketCard ticket={checkIn.data} onNext={() => {
      resetPreview();
      setPlate('');
      setName('');
      setPhone('');
      setNote('');
      setUnplated(false);
    }} />;
  }

  const error = checkIn.error ?? preview.error;
  return (
    <Card title="Cấp vé gửi xe khách">
      <form className="space-y-4" onSubmit={(event) => {
        event.preventDefault();
        const entry: GuestEntry = {
          vehicleTypeCode: type,
          plateNumber: unplated ? undefined : plate.trim(),
          guestName: name.trim() || undefined,
          guestPhone: phone.trim() || undefined,
          note: note.trim() || undefined,
        };
        preview.mutate(entry);
      }} onChange={resetPreview}>
        <fieldset disabled={preview.isPending || checkIn.isPending} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="space-y-1 text-sm font-medium">
              Loại xe
              <select className={INPUT} value={type} onChange={(event) => {
                setType(event.target.value);
                setUnplated(false);
              }}>
                {types.data?.map((item) => <option key={item.code} value={item.code}>{item.name}</option>)}
              </select>
            </label>
            <label className="space-y-1 text-sm font-medium">
              Biển số
              <input className={`${INPUT} font-mono uppercase`} value={plate} onChange={(event) => setPlate(event.target.value)}
                maxLength={32} autoComplete="off" required={!unplated} disabled={unplated} />
            </label>
          </div>
          {type === 'XE_DAP' && (
            <label className="flex items-center gap-2 text-sm font-medium">
              <input type="checkbox" checked={unplated} onChange={(event) => setUnplated(event.target.checked)} />
              Xe đạp không có biển
            </label>
          )}
          <details className="rounded-lg border border-slate-200 p-3">
            <summary className="cursor-pointer font-medium">Thông tin khách (tuỳ chọn)</summary>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className="space-y-1 text-sm font-medium">Họ tên
                <input className={INPUT} maxLength={100} value={name} onChange={(event) => setName(event.target.value)} />
              </label>
              <label className="space-y-1 text-sm font-medium">Số điện thoại
                <input className={INPUT} type="tel" maxLength={15} pattern="\+?[0-9]{6,15}" value={phone}
                  onChange={(event) => setPhone(event.target.value)} />
              </label>
              <label className="space-y-1 text-sm font-medium sm:col-span-2">Ghi chú
                <input className={INPUT} value={note} maxLength={255} onChange={(event) => setNote(event.target.value)} />
              </label>
            </div>
          </details>
        </fieldset>
        {types.isError && <Alert tone="error">{errorMessage(types.error)}</Alert>}
        {error && <Alert tone="error">{errorMessage(error)}</Alert>}
        {isApiError(error) && error.code === 'GUEST_PLATE_BELONGS_TO_STUDENT' && (
          <Button variant="secondary" onClick={onStudent}>Chuyển sang sinh viên</Button>
        )}
        {preview.data ? (
          <div className="space-y-3 rounded-lg bg-blue-50 p-4">
            <h3 className="font-semibold">Giá gửi xe khách</h3>
            <ul className="grid gap-2 text-sm sm:grid-cols-3">
              {preview.data.rules.map((rule) => (
                <li key={rule.upToMinutes}>Đến {formatDuration(rule.upToMinutes)} · <strong>{formatCash(rule.feePoints * preview.data!.cashPerPoint)}</strong></li>
              ))}
            </ul>
            <Button type="button" variant="success" loading={checkIn.isPending} onClick={() => {
              if (preview.variables) checkIn.mutate(preview.variables);
            }}>Xác nhận cho xe vào và cấp vé</Button>
          </div>
        ) : (
          <Button type="submit" loading={preview.isPending} disabled={!types.data}>Kiểm tra và xem giá</Button>
        )}
      </form>
    </Card>
  );
}
