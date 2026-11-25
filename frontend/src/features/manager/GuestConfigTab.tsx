import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { errorMessage, guestApi } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { GuestConfig } from '../../shared/types/guest';

const configKey = ['guest', 'config'] as const;

export function GuestConfigTab() {
  const settings = useQuery({ queryKey: configKey, queryFn: guestApi.settings });
  return (
    <Card title="Cấu hình gửi xe khách">
      {settings.isPending && <LoadingBlock />}
      {settings.isError && <QueryError error={settings.error} onRetry={() => void settings.refetch()} />}
      {settings.data && <GuestConfigForm settings={settings.data} />}
    </Card>
  );
}

function GuestConfigForm({ settings }: { settings: GuestConfig }) {
  const client = useQueryClient();
  const [enabled, setEnabled] = useState(settings.enabled);
  const [fee, setFee] = useState(String(settings.lostTicketFeeVnd));
  const save = useMutation({
    mutationFn: guestApi.saveSettings,
    onSuccess: (data) => client.setQueryData(configKey, data),
  });
  const value = Number(fee);
  const valid = fee !== '' && Number.isInteger(value) && value >= 0 && value <= 10_000_000;
  return (
    <form className="max-w-md space-y-4" onSubmit={(event) => {
      event.preventDefault();
      if (valid) save.mutate({ enabled, lostTicketFeeVnd: value });
    }}>
      <label className="flex items-center gap-2 font-medium">
        <input type="checkbox" checked={enabled} onChange={(event) => { save.reset(); setEnabled(event.target.checked); }} />
        Cho phép nhận xe khách
      </label>
      <p className="text-sm text-slate-600">Khi tạm ngừng nhận xe, khách đang gửi vẫn có thể thanh toán để lấy xe.</p>
      <label className="block space-y-1 text-sm font-medium">
        Phí mất vé (VNĐ)
        <input className="block w-full rounded-lg border border-slate-300 p-2" type="number" min={0} max={10_000_000}
          required value={fee} onChange={(event) => { save.reset(); setFee(event.target.value); }} />
      </label>
      {save.error && <Alert tone="error">{errorMessage(save.error)}</Alert>}
      {save.isSuccess && <Alert tone="success">Đã lưu cấu hình gửi xe khách.</Alert>}
      <Button type="submit" loading={save.isPending} disabled={!valid}>Lưu cấu hình</Button>
    </form>
  );
}
