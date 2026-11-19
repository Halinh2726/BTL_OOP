import { useEffect, useRef, useState } from 'react';

/**
 * Bề rộng thực (px) của một phần tử, cập nhật khi đổi cỡ cửa sổ. Biểu đồ SVG vẽ theo đúng số px này nên cột và chữ
 * không bị co giãn méo như khi dùng viewBox cố định.
 */
export function useElementWidth<T extends HTMLElement>(fallback = 640) {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(fallback);

  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    setWidth(element.clientWidth || fallback);
    if (typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.round(entry?.contentRect.width ?? 0) || fallback));
    observer.observe(element);
    return () => observer.disconnect();
  }, [fallback]);

  return { ref, width };
}
