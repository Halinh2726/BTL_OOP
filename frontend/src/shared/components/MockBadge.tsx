import { isMockMode } from '../api';

/** Nhắc rằng đang dùng dữ liệu giả lập (VITE_API_MODE=mock), không phải backend thật. */
export function MockBadge() {
  if (!isMockMode) return null;
  return (
    <span
      title="Dữ liệu giả lập trong trình duyệt — không gọi backend"
      className="ml-1 inline-flex rounded bg-amber-400 px-1.5 py-0.5 align-middle text-[10px] font-bold uppercase tracking-wider text-amber-950"
    >
      Mock
    </span>
  );
}
