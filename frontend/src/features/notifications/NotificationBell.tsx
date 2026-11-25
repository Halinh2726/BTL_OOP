import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Badge } from '../../shared/components/Badge';
import type { NotificationInbox } from '../../shared/types/api';
import { formatDateTime, NOTIFICATION_KIND_META } from '../../shared/utils/format';

/** Màu chấm theo loại thông báo (kind do backend đặt). */
const KIND_DOT: Record<string, string> = {
  XE_VAO: 'bg-sky-500',
  XE_RA: 'bg-emerald-600',
  SO_DU_THAP: 'bg-red-500',
  KHIEU_NAI: 'bg-amber-500',
  CAMERA: 'bg-indigo-600',
  BOI_THUONG: 'bg-emerald-500',
  VE_THANG: 'bg-blue-500',
  BAO_MAT: 'bg-rose-600',
  SU_CO: 'bg-amber-600',
  PHAN_CA: 'bg-violet-500',
  RUT_DIEM: 'bg-slate-500',
};

type Category = 'ALL' | 'PARKING' | 'PASS' | 'SUPPORT' | 'SECURITY';

const CATEGORY_TABS: Array<{ id: Category; label: string; kinds?: string[] }> = [
  { id: 'ALL', label: 'Tất cả' },
  { id: 'PARKING', label: 'Xe & điểm', kinds: ['XE_VAO', 'XE_RA', 'SO_DU_THAP', 'RUT_DIEM'] },
  { id: 'PASS', label: 'Vé tháng', kinds: ['VE_THANG'] },
  { id: 'SUPPORT', label: 'Sự cố & Khiếu nại', kinds: ['SU_CO', 'KHIEU_NAI', 'CAMERA', 'BOI_THUONG'] },
  { id: 'SECURITY', label: 'Bảo mật & Ca trực', kinds: ['BAO_MAT', 'PHAN_CA'] },
];

/**
 * Đợt 5 & mở rộng — chuông thông báo trên thanh đầu trang (mọi vai trò). Tự hỏi lại mỗi 5 giây & khi quay lại tab;
 * phân loại theo xe vào/ra, trừ điểm/sắp hết điểm, vé tháng, sự cố, camera, khiếu nại và bảo mật.
 */
export function NotificationBell() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [category, setCategory] = useState<Category>('ALL');
  const panel = useRef<HTMLDivElement>(null);
  const inbox = useQuery({
    queryKey: queryKeys.notifications,
    queryFn: api.notifications,
    staleTime: 0,
    refetchInterval: 5_000,
    refetchOnWindowFocus: true,
  });
  const update = (data: NotificationInbox) => queryClient.setQueryData(queryKeys.notifications, data);
  const read = useMutation({ mutationFn: (id: number) => api.markNotificationRead(id), onSuccess: update });
  const readAll = useMutation({ mutationFn: api.markAllNotificationsRead, onSuccess: update });
  const unread = inbox.data?.unread ?? 0;

  const activeFilter = CATEGORY_TABS.find((t) => t.id === category);
  const items = (inbox.data?.items ?? []).filter(
    (item) => !activeFilter?.kinds || activeFilter.kinds.includes(item.kind),
  );

  // Bấm ra ngoài hoặc nhấn Esc thì đóng
  useEffect(() => {
    if (!open) return undefined;
    const onClick = (event: MouseEvent) => {
      if (panel.current && !panel.current.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div className="relative" ref={panel}>
      <button
        type="button"
        onClick={() =>
          setOpen((value) => {
            const next = !value;
            if (next) void inbox.refetch();
            return next;
          })
        }
        aria-label={unread ? `Thông báo, ${unread} chưa đọc` : 'Thông báo'}
        aria-expanded={open}
        className="relative rounded-lg p-2 text-slate-600 hover:bg-slate-100"
      >
        <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" d="M14.857 17.082a23.848 23.848 0 0 0 5.454-1.31A8.967 8.967 0 0 1 18 9.75V9A6 6 0 0 0 6 9v.75a8.967 8.967 0 0 1-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 0 1-5.714 0m5.714 0a3 3 0 1 1-5.714 0" />
        </svg>
        {unread > 0 && (
          <span className="absolute -right-0.5 -top-0.5 min-w-5 rounded-full bg-red-600 px-1 text-center text-xs font-bold leading-5 text-white">
            {unread > 9 ? '9+' : unread}
          </span>
        )}
      </button>
      {open && (
        <div className="absolute right-0 z-50 mt-2 w-96 max-w-[calc(100vw-2rem)] rounded-xl bg-white shadow-xl ring-1 ring-slate-200">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-2.5">
            <p className="font-semibold text-slate-900">Thông báo</p>
            {unread > 0 && (
              <button type="button" onClick={() => readAll.mutate()} className="text-sm font-semibold text-blue-700 hover:underline">
                Đánh dấu đã đọc hết
              </button>
            )}
          </div>
          <div className="flex flex-wrap gap-1 border-b border-slate-100 px-3 py-2">
            {CATEGORY_TABS.map((tab) => (
              <button
                key={tab.id}
                type="button"
                onClick={() => setCategory(tab.id)}
                className={`rounded-full px-2.5 py-0.5 text-xs font-semibold transition-colors ${
                  category === tab.id ? 'bg-blue-700 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {tab.label}
              </button>
            ))}
          </div>
          <ul className="max-h-96 divide-y divide-slate-100 overflow-y-auto">
            {items.length === 0 && (
              <li className="px-4 py-6 text-center text-sm text-slate-500">Không có thông báo nào trong mục này.</li>
            )}
            {items.map((item) => {
              const meta = NOTIFICATION_KIND_META[item.kind];
              const badgeTone = meta?.tone === 'slate' ? 'gray' : (meta?.tone ?? 'gray');
              return (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => !item.read && read.mutate(item.id)}
                    className={`flex w-full gap-3 px-4 py-3 text-left hover:bg-slate-50 ${item.read ? '' : 'bg-blue-50/60'}`}
                  >
                    <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${item.read ? 'bg-slate-300' : KIND_DOT[item.kind] ?? 'bg-blue-500'}`} aria-hidden="true" />
                    <span className="min-w-0 flex-1 space-y-1">
                      <span className="flex flex-wrap items-center justify-between gap-1.5">
                        <Badge tone={badgeTone}>{meta?.label ?? item.kind}</Badge>
                        <span className="text-xs text-slate-400">{formatDateTime(item.createdAt)}</span>
                      </span>
                      <span className={`block text-sm ${item.read ? 'text-slate-700' : 'font-semibold text-slate-900'}`}>{item.title}</span>
                      <span className="block text-xs leading-relaxed text-slate-600">{item.body}</span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}
