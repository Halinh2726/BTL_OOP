import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { FeeRule } from '../../shared/types/api';
import { formatNumber } from '../../shared/utils/format';

/** "Trước 18:00" / "Từ 18:00" — mức phí áp dụng theo giờ xe RA. */
function describe(rules: FeeRule[], index: number): string {
  const rule = rules[index];
  const next = rules[index + 1];
  if (!rule) return '';
  if (rule.startTime === '00:00' && next) return `Ra trước ${next.startTime}`;
  return next ? `Ra từ ${rule.startTime} đến trước ${next.startTime}` : `Ra từ ${rule.startTime}`;
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
              <div key={rule.startTime} className="flex items-center justify-between py-2.5">
                <dt className="text-slate-600">{describe(rules.data, index)}</dt>
                <dd className="text-lg font-bold text-slate-900">{formatNumber(rule.feePoints)} điểm</dd>
              </div>
            ))}
          </dl>
          <p className="mt-3 text-sm text-slate-500">
            Phí tính theo giờ xe ra và tự trừ vào điểm khi bảo vệ xác nhận. Xe vào cần có ít nhất mức phí thấp nhất.
          </p>
        </>
      )}
    </Card>
  );
}
