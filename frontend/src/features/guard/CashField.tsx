import { useId } from 'react';
import { parseAmount } from '../manager/amount';
import { formatNumber } from '../../shared/utils/format';

/** Tối đa mỗi ca (cùng CashOnHand.MAX của backend). */
export const MAX_CASH = 50_000_000;

/** Đọc ô tiền: trống -> null; sai -> NaN để báo lỗi. */
export function readCash(raw: string): number | null {
  if (raw.trim() === '') return null;
  const value = parseAmount(raw);
  return value === null || value > MAX_CASH ? Number.NaN : value;
}

interface CashFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  hint?: string;
}

/** Ô nhập số tiền mặt (đồng): gõ "350000" hay "350.000" đều được, hiện lại dạng có dấu chấm cho dễ đọc. */
export function CashField({ label, value, onChange, hint }: CashFieldProps) {
  const id = useId();
  const parsed = readCash(value);
  const invalid = parsed !== null && Number.isNaN(parsed);
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      <div className="relative">
        <input
          id={id}
          inputMode="numeric"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="0"
          aria-invalid={invalid || undefined}
          className={`block w-full rounded-lg border px-3 py-2 pr-10 text-right font-mono text-xl focus:outline-none focus:ring-2 ${
            invalid ? 'border-red-400 focus:ring-red-200' : 'border-slate-300 focus:border-blue-600 focus:ring-blue-200'
          }`}
        />
        <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-slate-500">đ</span>
      </div>
      {invalid ? (
        <p className="text-sm text-red-600">Số tiền từ 0 đến {formatNumber(MAX_CASH)} đ, chỉ gồm chữ số</p>
      ) : (
        hint && <p className="text-sm text-slate-500">{hint}</p>
      )}
    </div>
  );
}
