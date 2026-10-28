import { forwardRef, useState, type FormEvent } from 'react';
import { Button } from '../../shared/components/Button';
import { normalizePlate } from '../../shared/utils/format';

const PLATE = /^[A-Z0-9]{5,20}$/;

interface PlateLookupFormProps {
  onLookup: (plate: string) => void;
  pending: boolean;
  disabled?: boolean;
}

/** Ô nhập biển số cỡ lớn + nút "Tra cứu". Biển số chuẩn hóa giống backend trước khi gửi. */
export const PlateLookupForm = forwardRef<HTMLInputElement, PlateLookupFormProps>(function PlateLookupForm(
  { onLookup, pending, disabled = false },
  ref,
) {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    const plate = normalizePlate(value);
    if (!PLATE.test(plate)) {
      setError(plate ? 'Biển số 5–20 ký tự, chỉ gồm chữ và số' : 'Vui lòng nhập biển số');
      return;
    }
    setError(null);
    onLookup(plate);
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-2">
      <label htmlFor="plate" className="block text-base font-semibold text-slate-700">
        Biển số xe
      </label>
      <div className="flex gap-3">
        <input
          ref={ref}
          id="plate"
          value={value}
          onChange={(event) => setValue(event.target.value.toUpperCase())}
          placeholder="29A12345"
          autoFocus
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          disabled={disabled}
          aria-invalid={error ? true : undefined}
          className="min-w-0 flex-1 rounded-xl border-2 border-slate-300 bg-white px-4 py-3 font-mono text-3xl font-bold uppercase tracking-widest focus:border-blue-600 focus:outline-none focus:ring-4 focus:ring-blue-100 disabled:bg-slate-100"
        />
        <Button type="submit" size="xl" loading={pending} disabled={disabled}>
          Tra cứu
        </Button>
      </div>
      {error && <p className="text-base text-red-600">{error}</p>}
    </form>
  );
});
