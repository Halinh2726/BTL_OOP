import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api, errorMessage, guestApi, isMockMode } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { FeeRule, VehicleTypeFeeSchedule } from '../../shared/types/api';
import { formatPoints } from '../../shared/utils/format';

const guestCatalogKey = ['guest', 'fee-catalog'] as const;

function durationLabel(minutes: number): string {
  if (minutes <= 0) return '0 phút';
  if (minutes % 60 === 0) return `${minutes / 60} giờ`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return hours > 0 ? `${hours}g ${mins}p` : `${mins} phút`;
}

function validateTiers(tiers: FeeRule[]): string | null {
  if (tiers.length === 0) return 'Bảng giá phải có ít nhất 1 bậc.';
  const sorted = [...tiers].sort((a, b) => a.upToMinutes - b.upToMinutes);
  for (let i = 0; i < sorted.length; i += 1) {
    const tier = sorted[i]!;
    if (!Number.isInteger(tier.upToMinutes) || tier.upToMinutes < 1 || tier.upToMinutes > 1440) {
      return 'Mốc thời lượng phải là số nguyên từ 1 đến 1440 phút.';
    }
    if (!Number.isInteger(tier.feePoints) || tier.feePoints < 100 || tier.feePoints > 10_000_000) {
      return 'Mức phí mỗi bậc phải từ 100 đến 10.000.000 điểm.';
    }
    if (i > 0) {
      const prev = sorted[i - 1]!;
      if (tier.upToMinutes === prev.upToMinutes) {
        return 'Các mốc thời lượng không được trùng nhau.';
      }
      if (tier.feePoints < prev.feePoints) {
        return 'Mức phí bậc sau không được thấp hơn bậc trước.';
      }
    }
  }
  if (sorted[sorted.length - 1]?.upToMinutes !== 1440) {
    return 'Bậc cuối cùng bắt buộc kết thúc ở 1440 phút (24 giờ).';
  }
  return null;
}

function VehicleFeeEditor({
  schedule,
  guest,
  onSaved,
}: {
  schedule: VehicleTypeFeeSchedule;
  guest: boolean;
  onSaved: (msg: string) => void;
}) {
  const queryClient = useQueryClient();
  const [tiers, setTiers] = useState<FeeRule[]>(() => schedule.rules.map((r) => ({ ...r })));

  useEffect(() => {
    setTiers(schedule.rules.map((r) => ({ ...r })));
  }, [schedule]);

  const validationError = validateTiers(tiers);

  const save = useMutation({
    mutationFn: () =>
      (guest ? guestApi.updateFeeRules : api.updateFeeRules)(
        schedule.vehicleTypeCode,
        [...tiers].sort((a, b) => a.upToMinutes - b.upToMinutes),
      ),
    onSuccess: async (data) => {
      queryClient.setQueryData(guest ? guestCatalogKey : queryKeys.managerFeeCatalog, data);
      if (!guest) {
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: queryKeys.feeRulesByVehicleType }),
          queryClient.invalidateQueries({ queryKey: queryKeys.feeRules }),
        ]);
      }
      onSaved(`Đã lưu bảng giá gửi lượt mới cho ${schedule.vehicleTypeName}.`);
    },
  });

  function updateTier(index: number, patch: Partial<FeeRule>) {
    setTiers((prev) => prev.map((item, i) => (i === index ? { ...item, ...patch } : item)));
  }

  function addTier() {
    if (tiers.length >= 6) return;
    const sorted = [...tiers].sort((a, b) => a.upToMinutes - b.upToMinutes);
    const last = sorted[sorted.length - 1] ?? { upToMinutes: 1440, feePoints: 5000 };
    const beforeLast = sorted.length >= 2 ? sorted[sorted.length - 2]! : { upToMinutes: 0, feePoints: last.feePoints };
    const midMinutes = Math.max(
      beforeLast.upToMinutes + 1,
      Math.min(1439, Math.round((beforeLast.upToMinutes + last.upToMinutes) / 2)),
    );
    if (midMinutes >= 1440) return;
    const next = [...sorted.slice(0, -1), { upToMinutes: midMinutes, feePoints: beforeLast.feePoints }, last];
    setTiers(next);
  }

  function removeTier(index: number) {
    if (tiers.length <= 1 || index === tiers.length - 1) return;
    setTiers((prev) => prev.filter((_, i) => i !== index));
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-slate-50/70 p-4 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-base font-bold text-slate-900">{schedule.vehicleTypeName}</h3>
          <p className="text-xs text-slate-500">
            Mã loại xe: <span className="font-mono font-semibold">{schedule.vehicleTypeCode}</span> · Mức tối thiểu khi
            {guest ? 'mỗi lượt' : 'vào bãi'}: <span className="font-semibold text-blue-700">{formatPoints(tiers[0]?.feePoints ?? 0)}</span>
          </p>
        </div>
        <div className="flex items-center gap-2">
          {tiers.length < 6 && (
            <Button size="sm" variant="secondary" type="button" onClick={addTier} disabled={save.isPending}>
              + Thêm bậc
            </Button>
          )}
          <Button
            size="sm"
            type="button"
            disabled={validationError !== null}
            loading={save.isPending}
            onClick={() => save.mutate()}
          >
            Lưu bảng giá
          </Button>
        </div>
      </div>

      <div className="space-y-2">
        {tiers.map((tier, index) => {
          const isLast = index === tiers.length - 1;
          const prev = tiers[index - 1];
          const rangeText = prev
            ? `Trên ${durationLabel(prev.upToMinutes)} → ${durationLabel(tier.upToMinutes)}`
            : `Đến ${durationLabel(tier.upToMinutes)}`;

          return (
            <div
              key={`${index}-${isLast ? 'day' : 'tier'}`}
              className="grid items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2.5 sm:grid-cols-12"
            >
              <div className="sm:col-span-5">
                <p className="text-sm font-medium text-slate-800">{rangeText}</p>
                {isLast && <p className="text-[11px] text-slate-400">Mốc cuối chu kỳ 24 giờ cố định ở 1440 phút</p>}
              </div>

              <label className="flex items-center gap-1.5 text-xs text-slate-600 sm:col-span-3">
                <span className="shrink-0">Đến (phút):</span>
                <input
                  type="number"
                  min={1}
                  max={1440}
                  disabled={isLast}
                  value={tier.upToMinutes}
                  aria-label={`Thời lượng bậc ${index + 1} của ${schedule.vehicleTypeName}`}
                  onChange={(e) => updateTier(index, { upToMinutes: Number(e.target.value) || 0 })}
                  className="w-full rounded-md border border-slate-300 px-2 py-1 text-right font-mono text-sm disabled:bg-slate-100 disabled:text-slate-500"
                />
              </label>

              <label className="flex items-center gap-1.5 text-xs text-slate-600 sm:col-span-3">
                <span className="shrink-0">Phí (điểm):</span>
                <input
                  inputMode="numeric"
                  value={String(tier.feePoints)}
                  aria-label={`Mức phí bậc ${index + 1} của ${schedule.vehicleTypeName}`}
                  onChange={(e) => {
                    const num = Number(e.target.value.replace(/\D/g, ''));
                    updateTier(index, { feePoints: Number.isFinite(num) ? num : 0 });
                  }}
                  className="w-full rounded-md border border-slate-300 px-2 py-1 text-right font-mono text-sm"
                />
              </label>

              <div className="flex justify-end sm:col-span-1">
                {!isLast && tiers.length > 1 && (
                  <button
                    type="button"
                    onClick={() => removeTier(index)}
                    className="rounded px-2 py-1 text-xs font-medium text-red-600 hover:bg-red-50"
                  >
                    Xóa
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {validationError && <Alert tone="error">{validationError}</Alert>}
      {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
    </div>
  );
}

/** Quản lý cấu hình bảng giá gửi lượt theo từng loại xe. */
export function FeeCatalogTab() {
  const [message, setMessage] = useState<string | null>(null);
  const [guest, setGuest] = useState(false);
  const catalog = useQuery({
    queryKey: guest ? guestCatalogKey : queryKeys.managerFeeCatalog,
    queryFn: guest ? guestApi.feeCatalog : api.managerFeeCatalog,
  });

  return (
    <Card title="Bảng giá gửi lượt theo loại xe">
      {!isMockMode && (
        <div className="mb-4 flex gap-2" role="group" aria-label="Nhóm áp dụng giá vé">
          {[false, true].map((value) => (
            <Button key={String(value)} size="sm" variant={guest === value ? 'primary' : 'secondary'}
              aria-pressed={guest === value} onClick={() => { setGuest(value); setMessage(null); }}>
              {value ? 'Khách' : 'Sinh viên'}
            </Button>
          ))}
        </div>
      )}
      {catalog.isPending && <LoadingBlock label="Đang tải bảng giá theo loại xe..." />}
      {catalog.isError && <QueryError error={catalog.error} onRetry={() => void catalog.refetch()} />}
      {catalog.data && (
        <div className="space-y-4">
          <p className="text-sm text-slate-600">
            {guest
              ? 'Phí gửi xe khách được quy đổi sang VNĐ theo tỷ lệ hiện hành. Lượt qua đêm dùng mức phí trong cấu hình giờ khóa.'
              : 'Quản lý điều chỉnh mức phí theo thời lượng gửi cho từng loại xe. Thay đổi áp dụng ngay cho lượt gửi thường của sinh viên.'}
          </p>
          {message && <Alert tone="success">{message}</Alert>}
          <div className="space-y-4">
            {catalog.data.schedules.map((schedule) => (
              <VehicleFeeEditor key={`${guest}-${schedule.vehicleTypeCode}`} schedule={schedule} guest={guest} onSaved={setMessage} />
            ))}
          </div>
        </div>
      )}
    </Card>
  );
}
