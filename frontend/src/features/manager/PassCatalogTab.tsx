import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { PassPriceView } from '../../shared/types/api';
import { formatPoints } from '../../shared/utils/format';

function PriceRow({ item, onSaved }: { item: PassPriceView; onSaved: (msg: string) => void }) {
  const queryClient = useQueryClient();
  const [points, setPoints] = useState(String(item.monthlyPoints));
  const save = useMutation({
    mutationFn: () => api.updatePassPrice(item.vehicleTypeCode, Number(points.replace(/\D/g, ''))),
    onSuccess: async (data) => {
      queryClient.setQueryData(queryKeys.managerPassCatalog, data);
      await queryClient.invalidateQueries({ queryKey: queryKeys.myPasses });
      onSaved(`Đã cập nhật giá vé tháng cho ${item.vehicleTypeName}.`);
    },
  });

  const numeric = Number(points.replace(/\D/g, ''));
  const valid = Number.isInteger(numeric) && numeric >= 1000 && numeric <= 10_000_000;

  return (
    <li className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
      <div>
        <p className="font-semibold text-slate-900">{item.vehicleTypeName}</p>
        <p className="text-xs text-slate-500">
          Mã: <span className="font-mono">{item.vehicleTypeCode}</span> · Hiện tại: {formatPoints(item.monthlyPoints)}
        </p>
      </div>
      <div className="flex items-center gap-2">
        <input
          inputMode="numeric"
          value={points}
          onChange={(e) => setPoints(e.target.value)}
          aria-label={`Giá vé tháng ${item.vehicleTypeName}`}
          className="w-36 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-right font-mono text-sm"
        />
        <Button size="sm" disabled={!valid} loading={save.isPending} onClick={() => save.mutate()}>
          Lưu giá
        </Button>
      </div>
      {save.isError && <div className="w-full"><Alert tone="error">{errorMessage(save.error)}</Alert></div>}
    </li>
  );
}

/** Quản lý bảng giá vé tháng theo loại xe. */
export function PassCatalogTab() {
  const [message, setMessage] = useState<string | null>(null);
  const catalog = useQuery({
    queryKey: queryKeys.managerPassCatalog,
    queryFn: api.managerPassCatalog,
  });

  return (
    <Card title="Bảng giá vé tháng theo loại xe">
      {catalog.isPending && <LoadingBlock />}
      {catalog.isError && <QueryError error={catalog.error} onRetry={() => void catalog.refetch()} />}
      {catalog.data && (
        <div className="space-y-4">
          {message && <Alert tone="success">{message}</Alert>}
          <ul className="space-y-2">
            {catalog.data.prices.map((p) => (
              <PriceRow key={`${p.vehicleTypeCode}-${p.monthlyPoints}`} item={p} onSaved={setMessage} />
            ))}
          </ul>
        </div>
      )}
    </Card>
  );
}
