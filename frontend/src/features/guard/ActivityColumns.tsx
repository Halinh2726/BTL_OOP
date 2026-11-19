import { useMemo, useState } from 'react';
import { Badge } from '../../shared/components/Badge';
import type { GateActivityItem } from '../../shared/types/api';
import { formatTime, normalizePlate } from '../../shared/utils/format';

const MAX_SUGGESTIONS = 6;

/**
 * Lịch sử xe mình đã cho vào / cho ra, chia HAI CỘT: cột Xe vào, cột Xe ra. Mỗi cột có ô tìm biển số riêng:
 * gõ vài số → hiện gợi ý các biển số CÓ TRONG CỘT ĐÓ, chạm gợi ý để lọc đúng xe đó.
 */
export function ActivityColumns({ items }: { items: GateActivityItem[] }) {
  return (
    <div className="grid gap-4 md:grid-cols-2">
      <ActivityColumn title="Xe vào" tone="blue" items={items.filter((item) => item.direction === 'VAO')} />
      <ActivityColumn title="Xe ra" tone="green" items={items.filter((item) => item.direction === 'RA')} />
    </div>
  );
}

function ActivityColumn({ title, tone, items }: { title: string; tone: 'blue' | 'green'; items: GateActivityItem[] }) {
  const [search, setSearch] = useState('');
  const [picked, setPicked] = useState<string | null>(null);
  const fragment = normalizePlate(search).replace(/[^A-Z0-9]/g, '');

  /** Gợi ý: biển số trong cột chứa chuỗi vừa gõ, biển KẾT THÚC bằng chuỗi đó lên trước (giống gợi ý ở cổng). */
  const suggestions = useMemo(() => {
    if (fragment.length < 2 || picked) return [];
    const unique = new Map<string, string>();
    items.forEach((item) => {
      if (item.plateNumber.includes(fragment)) unique.set(item.plateNumber, item.plateDisplay);
    });
    return [...unique.entries()]
      .sort(([a], [b]) => Number(b.endsWith(fragment)) - Number(a.endsWith(fragment)) || a.localeCompare(b))
      .slice(0, MAX_SUGGESTIONS);
  }, [items, fragment, picked]);

  const shown = items.filter((item) => (picked ? item.plateNumber === picked : !fragment || item.plateNumber.includes(fragment)));

  return (
    <section className="rounded-xl ring-1 ring-slate-200">
      <header className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
        <h3 className="font-semibold">{title}</h3>
        <Badge tone={tone}>{items.length} lượt</Badge>
      </header>
      <div className="space-y-2 p-3">
        <div className="relative">
          <input
            value={search}
            onChange={(event) => {
              setSearch(event.target.value.toUpperCase());
              setPicked(null);
            }}
            placeholder="Tìm biển số (gõ vài số cuối)"
            aria-label={`Tìm biển số trong cột ${title}`}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 font-mono uppercase focus:border-blue-600 focus:outline-none"
          />
          {(search || picked) && (
            <button
              type="button"
              aria-label="Xóa tìm kiếm"
              onClick={() => {
                setSearch('');
                setPicked(null);
              }}
              className="absolute inset-y-0 right-2 px-2 text-slate-400 hover:text-slate-700"
            >
              ✕
            </button>
          )}
        </div>
        {suggestions.length > 0 && (
          <ul className="flex flex-wrap gap-2" aria-label={`Gợi ý biển số cột ${title}`}>
            {suggestions.map(([plate, display]) => (
              <li key={plate}>
                <button
                  type="button"
                  onClick={() => {
                    setPicked(plate);
                    setSearch(display);
                  }}
                  className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 font-mono font-bold hover:border-blue-500 hover:bg-blue-50"
                >
                  {display}
                </button>
              </li>
            ))}
          </ul>
        )}
        {shown.length === 0 ? (
          <p className="py-4 text-center text-sm text-slate-500">{items.length === 0 ? 'Chưa có lượt nào.' : 'Không có biển số khớp.'}</p>
        ) : (
          <ul className="max-h-96 divide-y divide-slate-100 overflow-y-auto">
            {shown.map((item) => (
              <li key={`${item.sessionId}-${item.direction}`} className="flex items-center justify-between gap-3 py-2">
                <div className="min-w-0">
                  <p className="font-mono text-lg font-bold">{item.plateDisplay}</p>
                  <p className="truncate text-sm text-slate-500">
                    {item.fullName} ({item.studentCode})
                  </p>
                </div>
                <span className="text-sm font-semibold text-slate-700">{formatTime(item.at)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
