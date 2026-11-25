import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { FeeRule } from '../../shared/types/api';
import { formatNumber } from '../../shared/utils/format';

function durationLabel(minutes: number): string {
  if (minutes % 60 === 0) return `${minutes / 60} giờ`;
  const hours = Math.floor(minutes / 60);
  const mins = minutes % 60;
  return hours > 0 ? `${hours}g ${mins}p` : `${mins} phút`;
}

function describe(rules: FeeRule[], index: number): string {
  const rule = rules[index];
  if (!rule) return '';
  const end = durationLabel(rule.upToMinutes);
  const previous = rules[index - 1];
  return previous ? `Trên ${durationLabel(previous.upToMinutes)} đến ${end}` : `Đến ${end}`;
}

export function FeeTable() {
  const schedules = useQuery({
    queryKey: queryKeys.feeRulesByVehicleType,
    queryFn: api.feeRulesByVehicleType,
    staleTime: 30_000,
  });
  const policy = useQuery({ queryKey: queryKeys.parkingPolicy, queryFn: api.parkingPolicy });
  const [selectedType, setSelectedType] = useState<string>('XE_MAY');

  const activeSchedule =
    schedules.data?.find((s) => s.vehicleTypeCode === selectedType) ?? schedules.data?.[0];

  return (
    <Card title="Bảng phí gửi xe theo loại xe">
      {schedules.isPending && <LoadingBlock />}
      {schedules.isError && <QueryError error={schedules.error} onRetry={() => void schedules.refetch()} />}
      {schedules.data && activeSchedule && (
        <>
          <div className="mb-3 flex flex-wrap gap-2" role="tablist" aria-label="Loại xe">
            {schedules.data.map((item) => {
              const active = item.vehicleTypeCode === activeSchedule.vehicleTypeCode;
              return (
                <button
                  key={item.vehicleTypeCode}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setSelectedType(item.vehicleTypeCode)}
                  className={`rounded-full px-3.5 py-1 text-xs font-semibold ring-1 ring-inset transition-colors ${
                    active
                      ? 'bg-blue-700 text-white ring-blue-700'
                      : 'bg-white text-slate-600 ring-slate-300 hover:bg-slate-50'
                  }`}
                >
                  {item.vehicleTypeName}
                </button>
              );
            })}
          </div>
          <dl className="divide-y divide-slate-100">
            {activeSchedule.rules.map((rule, index) => (
              <div key={rule.upToMinutes} className="flex items-center justify-between py-2.5">
                <dt className="text-slate-600">{describe(activeSchedule.rules, index)}</dt>
                <dd className="text-lg font-bold text-slate-900">{formatNumber(rule.feePoints)} điểm</dd>
              </div>
            ))}
            {policy.data && (
              <div className="flex items-center justify-between py-2.5">
                <dt className="font-medium text-amber-800">Gửi qua đêm (lấy xe từ ngày hôm sau)</dt>
                <dd className="text-lg font-bold text-amber-700">{formatNumber(policy.data.overnightFeePoints)} điểm</dd>
              </div>
            )}
          </dl>
          <div className="mt-3 space-y-1 text-sm text-slate-500">
            <p>
              Lượt gửi trong ngày tính theo thời lượng của từng loại xe. Lượt gửi qua đêm áp dụng giá riêng trong bảng. Xe
              có vé tháng còn hiệu lực được miễn phí.
            </p>
            {policy.data?.curfewEnabled && (
              <p className="font-medium text-amber-800">
                Lưu ý: Bãi xe khóa lấy xe trong ngày từ {policy.data.checkoutCurfewTime}. Xe chưa lấy trước{' '}
                {policy.data.checkoutCurfewTime} sẽ lấy vào ngày hôm sau và áp dụng phí qua đêm (
                {formatNumber(policy.data.overnightFeePoints)} điểm).
              </p>
            )}
          </div>
        </>
      )}
    </Card>
  );
}
