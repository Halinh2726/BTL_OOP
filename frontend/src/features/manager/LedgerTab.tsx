import { useInfiniteQuery, useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { useDebouncedValue } from '../../shared/hooks/useDebouncedValue';
import type { LedgerQuery, TransactionType } from '../../shared/types/api';
import { TRANSACTION_LABELS } from '../../shared/utils/format';
import { RANGE_PRESETS, matchingPreset, presetRange, vietnamToday } from '../owner/revenue/revenueMath';
import { LedgerTable } from './LedgerTable';

const PAGE_SIZE = 30;
const TYPES = Object.keys(TRANSACTION_LABELS) as TransactionType[];

/** Tải file về máy từ một Blob (giữ nguyên tên file gợi ý). */
function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.click();
  URL.revokeObjectURL(url);
}

/**
 * Đợt 4a — sổ giao dịch toàn hệ thống: lọc theo khoảng ngày, loại, MSSV; "Xem thêm" theo trang; xuất CSV đúng bộ lọc
 * đang chọn (tối đa 93 ngày) để nộp báo cáo tháng.
 */
export function LedgerTab() {
  const today = vietnamToday();
  const initial = presetRange('THIS_MONTH', today);
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [type, setType] = useState<TransactionType | undefined>();
  const [studentCode, setStudentCode] = useState('');
  const debouncedCode = useDebouncedValue(studentCode.trim());
  const filter: Omit<LedgerQuery, 'before'> = {
    from: from || undefined, to: to || undefined, type, studentCode: debouncedCode || undefined,
  };
  const preset = matchingPreset(from, to, today);

  const ledger = useInfiniteQuery({
    queryKey: queryKeys.ledger(filter),
    queryFn: ({ pageParam }) => api.ledger({ ...filter, limit: PAGE_SIZE, before: pageParam }),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last.hasMore && last.nextBefore !== null ? last.nextBefore : undefined),
  });
  const items = ledger.data?.pages.flatMap((page) => page.items) ?? [];

  const exportCsv = useMutation({
    mutationFn: () => api.exportLedger(filter),
    onSuccess: (blob) => saveBlob(blob, `so-giao-dich_${from}_${to}.csv`),
  });

  return (
    <Card
      title="Sổ giao dịch toàn hệ thống"
      actions={
        <Button size="sm" variant="secondary" loading={exportCsv.isPending} disabled={!from || !to} onClick={() => exportCsv.mutate()}>
          Xuất CSV
        </Button>
      }
    >
      <div className="space-y-4">
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-wrap gap-1.5" role="group" aria-label="Chọn nhanh khoảng ngày">
            {RANGE_PRESETS.map((item) => (
              <button
                key={item.id}
                type="button"
                aria-pressed={preset === item.id}
                onClick={() => {
                  const range = presetRange(item.id, today);
                  setFrom(range.from);
                  setTo(range.to);
                }}
                className={`rounded-full px-3 py-1.5 text-sm font-semibold ring-1 ring-inset ${
                  preset === item.id ? 'bg-blue-700 text-white ring-blue-700' : 'bg-white text-slate-600 ring-slate-300 hover:bg-slate-50'
                }`}
              >
                {item.label}
              </button>
            ))}
          </div>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Từ ngày
            <input type="date" value={from} max={to || today} onChange={(e) => setFrom(e.target.value)} className="block rounded-lg border border-slate-300 px-3 py-1.5" />
          </label>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Đến ngày
            <input type="date" value={to} min={from} max={today} onChange={(e) => setTo(e.target.value)} className="block rounded-lg border border-slate-300 px-3 py-1.5" />
          </label>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            Loại giao dịch
            <select
              value={type ?? ''}
              onChange={(e) => setType((e.target.value || undefined) as TransactionType | undefined)}
              className="block rounded-lg border border-slate-300 bg-white px-3 py-1.5"
            >
              <option value="">Tất cả</option>
              {TYPES.map((t) => <option key={t} value={t}>{TRANSACTION_LABELS[t]}</option>)}
            </select>
          </label>
          <label className="space-y-1 text-sm font-medium text-slate-700">
            MSSV
            <input
              value={studentCode}
              onChange={(e) => setStudentCode(e.target.value)}
              placeholder="Tất cả sinh viên"
              className="block w-40 rounded-lg border border-slate-300 px-3 py-1.5 uppercase placeholder:normal-case"
            />
          </label>
        </div>
        {exportCsv.isError && <Alert tone="error">{errorMessage(exportCsv.error)}</Alert>}
        {ledger.isPending && <LoadingBlock />}
        {ledger.isError && <QueryError error={ledger.error} onRetry={() => void ledger.refetch()} />}
        {ledger.isSuccess && items.length === 0 && <p className="py-6 text-center text-slate-500">Không có giao dịch nào khớp bộ lọc.</p>}
        {items.length > 0 && <LedgerTable items={items} />}
        {ledger.hasNextPage && (
          <div className="text-center">
            <Button variant="secondary" loading={ledger.isFetchingNextPage} onClick={() => void ledger.fetchNextPage()}>
              Xem thêm
            </Button>
          </div>
        )}
      </div>
    </Card>
  );
}
