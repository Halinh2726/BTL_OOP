import { useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { REASON_LABELS, formatCash, formatPoints } from '../../shared/utils/format';

interface VerdictBannerProps {
  allowed: boolean;
  allowedText: string;
  reason: string | null;
  /** Đợt 2: số điểm còn thiếu khi không đủ điểm — để nói ngay với sinh viên cần nạp thêm bao nhiêu. */
  shortfall?: number | null;
  /** Giờ khóa lấy xe trong ngày ("HH:mm") khi reason === 'CHECKOUT_CURFEW'. */
  curfewTime?: string | null;
}

/** Kết luận to, rõ để bảo vệ nhìn một lần là biết: xanh = cho qua, đỏ = lý do không cho. */
export function VerdictBanner({ allowed, allowedText, reason, shortfall, curfewTime }: VerdictBannerProps) {
  const rate = useQuery({ queryKey: queryKeys.exchangeRate, queryFn: api.exchangeRate, staleTime: Infinity });

  if (allowed) {
    return (
      <div className="rounded-xl bg-emerald-600 px-5 py-4 text-center text-2xl font-bold text-white" role="status">
        ✓ {allowedText}
      </div>
    );
  }
  if (reason === 'CHECKOUT_CURFEW') {
    return (
      <div className="rounded-xl bg-red-600 px-5 py-4 text-center text-white" role="alert">
        <p className="text-2xl font-bold">✗ Đã khóa lấy xe từ {curfewTime ?? '20:00'}</p>
        <p className="mt-1 text-base">
          Không cho lấy xe sau giờ quy định. Sinh viên quay lại lấy xe vào ngày hôm sau (áp dụng phí qua đêm).
        </p>
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
          Sinh viên cần nạp thêm ít nhất {formatCash(shortfall * (rate.data?.cashPerPoint ?? 1))} (qua VietQR hoặc tại quầy),
          hoặc thanh toán trực tiếp tại cổng bằng VietQR / tiền mặt.
        </p>
      )}
    </div>
  );
}
