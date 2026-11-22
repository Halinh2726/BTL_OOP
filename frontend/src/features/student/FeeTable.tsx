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
          </dl>
          <p className="mt-3 text-sm text-slate-500">
            Mỗi 24 giờ tròn tính theo bậc cuối. Phần thời gian còn lại tính theo bảng trên. Phí được trừ khi bảo vệ xác nhận xe ra. Vé tháng còn hiệu lực được miễn phí.
          </p>
        </>
      )}
    </Card>
  );
}
