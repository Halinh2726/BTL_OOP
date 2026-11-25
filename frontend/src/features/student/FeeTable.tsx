import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { FeeRule } from '../../shared/types/api';
import { formatNumber } from '../../shared/utils/format';

function durationLabel(minutes: number): string {
  if (minutes % 60 === 0) return `${minutes / 60} giờ`;
  return `${minutes} phút`;
}

function describe(rules: FeeRule[], index: number): string {
  const rule = rules[index];
  if (!rule) return '';
  const end = durationLabel(rule.upToMinutes);
  const previous = rules[index - 1];
  return previous ? `Trên ${durationLabel(previous.upToMinutes)} đến ${end}` : `Đến ${end}`;
}

export function FeeTable() {
  const rules = useQuery({ queryKey: queryKeys.feeRules, queryFn: api.feeRules, staleTime: Infinity });
  const policy = useQuery({ queryKey: queryKeys.parkingPolicy, queryFn: api.parkingPolicy });

  return (
    <Card title="Bảng phí gửi xe">
      {rules.isPending && <LoadingBlock />}
      {rules.isError && <QueryError error={rules.error} onRetry={() => void rules.refetch()} />}
      {rules.data && (
        <>
          <dl className="divide-y divide-slate-100">
            {rules.data.map((rule, index) => (
              <div key={rule.upToMinutes} className="flex items-center justify-between py-2.5">
                <dt className="text-slate-600">{describe(rules.data, index)}</dt>
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
            <p>Lượt gửi trong ngày tính theo thời lượng. Lượt gửi qua đêm áp dụng giá riêng trong bảng. Xe có vé tháng còn hiệu lực được miễn phí.</p>
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
