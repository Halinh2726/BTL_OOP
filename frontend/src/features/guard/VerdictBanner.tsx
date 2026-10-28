import { REASON_LABELS } from '../../shared/utils/format';

/** Kết luận to, rõ để bảo vệ nhìn một lần là biết: xanh = cho qua, đỏ = lý do không cho. */
export function VerdictBanner({ allowed, allowedText, reason }: { allowed: boolean; allowedText: string; reason: string | null }) {
  if (allowed) {
    return (
      <div className="rounded-xl bg-emerald-600 px-5 py-4 text-center text-2xl font-bold text-white" role="status">
        ✓ {allowedText}
      </div>
    );
  }
  return (
    <div className="rounded-xl bg-red-600 px-5 py-4 text-center text-2xl font-bold text-white" role="alert">
      ✗ {reason ? (REASON_LABELS[reason] ?? reason) : 'Không cho qua'}
    </div>
  );
}
