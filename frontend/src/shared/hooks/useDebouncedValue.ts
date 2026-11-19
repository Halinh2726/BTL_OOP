import { useEffect, useState } from 'react';

/** Giá trị chỉ cập nhật sau khi người dùng ngừng gõ `delayMs` — tránh gọi API theo từng phím. */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
