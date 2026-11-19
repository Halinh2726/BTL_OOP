import { formatCash } from '../../shared/utils/format';

/** Chênh lệch tiền nhận ca so với tiền ca trước bàn giao: khớp thì không hiện gì, lệch thì tô đỏ. */
export function CashDifference({ difference }: { difference: number | null }) {
  if (difference === null || difference === 0) return null;
  return (
    <span className="block text-xs font-semibold text-red-700">
      {difference > 0 ? 'Thừa ' : 'Thiếu '}
      {formatCash(Math.abs(difference))} so với bàn giao
    </span>
  );
}
