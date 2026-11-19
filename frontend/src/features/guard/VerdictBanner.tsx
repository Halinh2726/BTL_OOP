import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { REASON_LABELS, formatCash, formatPoints } from '../../shared/utils/format';

interface VerdictBannerProps {
  allowed: boolean;
  allowedText: string;
  reason: string | null;
  /** Đợt 2: số điểm còn thiếu khi không đủ điểm — để nói ngay với sinh viên cần đổi thêm bao nhiêu. */
  shortfall?: number | null;
}

/** Kết luận to, rõ để bảo vệ nhìn một lần là biết: xanh = cho qua, đỏ = lý do không cho. */
export function VerdictBanner({ allowed, allowedText, reason, shortfall }: VerdictBannerProps) {
  const rate = useQuery({ queryKey: queryKeys.exchangeRate, queryFn: api.exchangeRate, staleTime: Infinity });

  if (allowed) {
    return (
      <div className="rounded-xl bg-emerald-600 px-5 py-4 text-center text-2xl font-bold text-white" role="status">
        ✓ {allowedText}
      </div>
    );
  }
  const missing = reason === 'INSUFFICIENT_POINTS' && shortfall !== null && shortfall !== undefined && shortfall > 0;
  return (
    <div className="rounded-xl bg-red-600 px-5 py-4 text-center text-white" role="alert">
      <p className="text-2xl font-bold">
        ✗ {missing ? `Thiếu ${formatPoints(shortfall)}` : reason ? (REASON_LABELS[reason] ?? reason) : 'Không cho qua'}
      </p>
      {missing && (
        <p className="mt-1 text-base">
          Sinh viên cần đổi thêm ít nhất {formatCash(shortfall * (rate.data?.cashPerPoint ?? 1))} tại quầy quản lý bãi xe.
        </p>
      )}
    </div>
  );
}
