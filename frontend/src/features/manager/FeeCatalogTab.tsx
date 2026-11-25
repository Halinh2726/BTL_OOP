import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { FeeRule, VehicleTypeFeeSchedule } from '../../shared/types/api';
import { FIXED_TIME_SLOTS, toFixedTimeSlotRules } from '../../shared/utils/feeSlots';
import { formatPoints } from '../../shared/utils/format';

function validateTimeSlotPrices(tiers: FeeRule[]): string | null {
  if (tiers.length !== FIXED_TIME_SLOTS.length) {
    return 'Bảng giá phải gồm đủ 3 khung giờ cố định.';
  }
  for (let i = 0; i < tiers.length; i += 1) {
    const tier = tiers[i]!;
    if (!Number.isInteger(tier.feePoints) || tier.feePoints < 100 || tier.feePoints > 10_000_000) {
      return 'Mức phí mỗi khung phải từ 100 đến 10.000.000 điểm.';
    }
    if (i > 0 && tier.feePoints < tiers[i - 1]!.feePoints) {
      return 'Mức phí khung sau không được thấp hơn khung trước.';
    }
  }
  return null;
}

function VehicleFeeEditor({
  schedule,
  onSaved,
}: {
  schedule: VehicleTypeFeeSchedule;
  onSaved: (msg: string) => void;
}) {
  const queryClient = useQueryClient();
  const [tiers, setTiers] = useState<FeeRule[]>(() => toFixedTimeSlotRules(schedule.rules));

  useEffect(() => {
    setTiers(toFixedTimeSlotRules(schedule.rules));
  }, [schedule]);

  const validationError = validateTimeSlotPrices(tiers);

  const save = useMutation({
    mutationFn: () => api.updateFeeRules(schedule.vehicleTypeCode, tiers),
    onSuccess: async (data) => {
      queryClient.setQueryData(queryKeys.managerFeeCatalog, data);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.feeRulesByVehicleType }),
        queryClient.invalidateQueries({ queryKey: queryKeys.feeRules }),
      ]);
      onSaved(`Đã lưu bảng giá gửi lượt mới cho ${schedule.vehicleTypeName}.`);
    },
  });

  function updateFee(index: number, feePoints: number) {
    setTiers((prev) => prev.map((item, i) => (i === index ? { ...item, feePoints } : item)));
  }

  return (
    <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50/70 p-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-base font-bold text-slate-900">{schedule.vehicleTypeName}</h3>
          <p className="text-xs text-slate-500">
            Mã loại xe: <span className="font-mono font-semibold">{schedule.vehicleTypeCode}</span> · Mức tối thiểu khi vào bãi:{' '}
            <span className="font-semibold text-blue-700">{formatPoints(tiers[0]?.feePoints ?? 0)}</span>
          </p>
        </div>
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

      <div className="space-y-2">
        {FIXED_TIME_SLOTS.map((slot, index) => {
          const tier = tiers[index] ?? { upToMinutes: slot.upToMinutes, feePoints: slot.defaultPoints };
          return (
            <div
              key={slot.upToMinutes}
              className={`grid items-center gap-3 rounded-lg border px-3 py-2.5 sm:grid-cols-12 ${
                slot.highlight ? 'border-amber-200 bg-amber-50/50' : 'border-slate-200 bg-white'
              }`}
            >
              <div className="sm:col-span-7">
                <p className={`text-sm font-semibold ${slot.highlight ? 'text-amber-900' : 'text-slate-800'}`}>
                  {slot.label}
                </p>
                <p className="text-xs text-slate-500">{slot.description}</p>
              </div>

              <label className="flex items-center justify-end gap-2 text-xs text-slate-600 sm:col-span-5">
                <span className="shrink-0 font-medium">Giá ({slot.unitSuffix}):</span>
                <input
                  inputMode="numeric"
                  value={String(tier.feePoints)}
                  aria-label={`${slot.label} của ${schedule.vehicleTypeName}`}
                  onChange={(e) => {
                    const num = Number(e.target.value.replace(/\D/g, ''));
                    updateFee(index, Number.isFinite(num) ? num : 0);
                  }}
                  className="w-32 rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-right font-mono text-sm font-semibold text-slate-900"
                />
              </label>
            </div>
          );
        })}
      </div>

      {validationError && <Alert tone="error">{validationError}</Alert>}
      {save.isError && <Alert tone="error">{errorMessage(save.error)}</Alert>}
    </div>
  );
}

/** Quản lý cấu hình bảng giá gửi lượt theo khung giờ cố định cho từng loại xe (áp dụng đồng bộ cho sinh viên và khách). */
export function FeeCatalogTab() {
  const [message, setMessage] = useState<string | null>(null);
  const catalog = useQuery({
    queryKey: queryKeys.managerFeeCatalog,
    queryFn: api.managerFeeCatalog,
  });

  return (
    <Card title="Bảng giá gửi lượt theo loại xe">
      {catalog.isPending && <LoadingBlock label="Đang tải bảng giá theo loại xe..." />}
      {catalog.isError && <QueryError error={catalog.error} onRetry={() => void catalog.refetch()} />}
      {catalog.data && (
        <div className="space-y-4">
          <div className="space-y-1 rounded-lg border border-blue-100 bg-blue-50/70 p-3 text-sm text-slate-700">
            <p>
              Giá vé tính theo <strong>khung giờ lúc lấy xe</strong> cho từng loại xe (áp dụng đồng bộ cho cả sinh viên và khách).
            </p>
            <p className="text-xs text-slate-600">
              Nếu xe để <strong>qua đêm</strong> (sau 22:00 sang ngày hôm sau), phí thanh toán được <strong>cộng dồn</strong> theo
              từng đêm + khung giờ ngày lấy xe. Ví dụ gửi từ Thứ 2 đến 19:00 Thứ 4: <strong>15.000 + 15.000 + 5.000 = 35.000 điểm</strong>.
            </p>
          </div>
          {message && <Alert tone="success">{message}</Alert>}
          <div className="space-y-4">
            {catalog.data.schedules.map((schedule) => (
              <VehicleFeeEditor key={schedule.vehicleTypeCode} schedule={schedule} onSaved={setMessage} />
            ))}
          </div>
        </div>
      )}
    </Card>
  );
}
