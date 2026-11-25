import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { FIXED_TIME_SLOTS, toFixedTimeSlotRules } from '../../shared/utils/feeSlots';
import { formatNumber } from '../../shared/utils/format';

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
  const fixedRules = activeSchedule ? toFixedTimeSlotRules(activeSchedule.rules) : [];
  const eveningFee = fixedRules[1]?.feePoints ?? 5000;
  const overnightFee = fixedRules[2]?.feePoints ?? 15000;

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
            {FIXED_TIME_SLOTS.map((slot, index) => {
              const feePoints = fixedRules[index]?.feePoints ?? slot.defaultPoints;
              return (
                <div key={slot.upToMinutes} className="flex items-center justify-between py-2.5">
                  <dt className={slot.highlight ? 'font-medium text-amber-800' : 'text-slate-600'}>{slot.label}</dt>
                  <dd className={`text-lg font-bold ${slot.highlight ? 'text-amber-700' : 'text-slate-900'}`}>
                    {formatNumber(feePoints)} {slot.unitSuffix}
                  </dd>
                </div>
              );
            })}
          </dl>
          <div className="mt-3 space-y-1 text-sm text-slate-500">
            <p>
              Lượt gửi trong ngày tính theo khung giờ lúc lấy xe. Nếu để qua đêm sang ngày hôm sau, phí được cộng dồn theo
              từng đêm + khung giờ lúc lấy xe (VD: gửi Thứ 2, lấy lúc 19:00 Thứ 4 ={' '}
              {formatNumber(overnightFee)} + {formatNumber(overnightFee)} + {formatNumber(eveningFee)} ={' '}
              {formatNumber(overnightFee * 2 + eveningFee)} điểm). Xe có vé tháng còn hiệu lực được miễn phí.
            </p>
            {policy.data?.curfewEnabled && (
              <p className="font-medium text-amber-800">
                Lưu ý: Bãi xe khóa lấy xe trong ngày từ {policy.data.checkoutCurfewTime}. Xe chưa lấy trước{' '}
                {policy.data.checkoutCurfewTime} sẽ lấy vào ngày hôm sau và cộng dồn phí qua đêm (
                {formatNumber(overnightFee)} điểm/đêm).
              </p>
            )}
          </div>
        </>
      )}
    </Card>
  );
}
