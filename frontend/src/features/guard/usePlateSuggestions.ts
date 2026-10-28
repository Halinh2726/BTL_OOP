import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../../shared/api';
import { normalizePlate } from '../../shared/utils/format';

const DEBOUNCE_MS = 250;
const MIN_LENGTH = 2;

/**
 * Gợi ý biển số khi bảo vệ gõ (GET /guard/plates): chờ ngừng gõ 250 ms mới gọi, từ 2 ký tự trở lên.
 * Lỗi gợi ý không chặn gì — bảo vệ vẫn gõ đủ biển và tra cứu như bình thường.
 */
export function usePlateSuggestions(input: string, parkedOnly: boolean, enabled: boolean) {
  const fragment = normalizePlate(input);
  const [debounced, setDebounced] = useState(fragment);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(fragment), DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [fragment]);

  const active = enabled && debounced.length >= MIN_LENGTH && /^[A-Z0-9]+$/.test(debounced);
  const query = useQuery({
    queryKey: ['guard', 'plates', debounced, parkedOnly],
    queryFn: () => api.suggestPlates(debounced, parkedOnly),
    enabled: active,
    staleTime: 5_000,
    retry: false,
  });

  return {
    suggestions: active && !query.isError ? (query.data ?? []) : [],
    /** Đã có kết quả cho đúng nội dung đang gõ (để báo "không có xe nào khớp"). */
    settled: active && debounced === fragment && query.isSuccess,
  };
}
