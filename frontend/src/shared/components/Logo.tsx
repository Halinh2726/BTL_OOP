import { useState } from 'react';

/** Logo Học viện (frontend/public/logo-ptit.png). Thiếu file thì hiện chữ "P" thay thế, giao diện không vỡ. */
export function Logo({ className = 'h-9 w-9' }: { className?: string }) {
  const [missing, setMissing] = useState(false);
  if (missing) {
    return (
      <div className={`flex shrink-0 items-center justify-center rounded-lg bg-blue-700 font-bold text-white ${className}`}>P</div>
    );
  }
  return (
    <img
      src="/logo-ptit.png"
      alt="Học viện Công nghệ Bưu chính Viễn thông"
      className={`shrink-0 rounded-lg bg-white object-contain ${className}`}
      onError={() => setMissing(true)}
    />
  );
}
