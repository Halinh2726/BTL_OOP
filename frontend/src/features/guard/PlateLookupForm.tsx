import { forwardRef, useState, type FormEvent } from 'react';
import { Button } from '../../shared/components/Button';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import { normalizePlate } from '../../shared/utils/format';
import { QrScannerDialog } from './QrScannerDialog';
import { usePlateSuggestions } from './usePlateSuggestions';

const PLATE = /^[A-Z0-9]{5,20}$/;

interface PlateLookupFormProps {
  onLookup: (plate: string) => void;
  pending: boolean;
  disabled?: boolean;
  /** Màn hình xe ra: chỉ gợi ý xe đang trong bãi. */
  parkedOnly: boolean;
  /** Bảo vệ bắt đầu gõ biển số khác -> bỏ kết quả tra cứu của xe trước để không nhìn nhầm. */
  onEdit: () => void;
}

/**
 * Ba cách nhập biển số cho bảo vệ: quét mã QR trên điện thoại sinh viên, gõ vài số cuối rồi chạm gợi ý,
 * hoặc gõ đủ biển. Biển số chuẩn hóa giống backend trước khi tra cứu.
 */
export const PlateLookupForm = forwardRef<HTMLInputElement, PlateLookupFormProps>(function PlateLookupForm(
  { onLookup, pending, disabled = false, parkedOnly, onEdit },
  ref,
) {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [showSuggestions, setShowSuggestions] = useState(true);
  const [scanning, setScanning] = useState(false);
  const typeName = useVehicleTypeName();
  const { suggestions, settled } = usePlateSuggestions(value, parkedOnly, showSuggestions && !disabled);

  function lookup(plate: string) {
    setValue(plate);
    setError(null);
    setShowSuggestions(false);
    onLookup(plate);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const plate = normalizePlate(value);
    if (PLATE.test(plate)) {
      lookup(plate);
      return;
    }
    // Gõ mấy số cuối mà chỉ khớp đúng một xe -> Enter chọn luôn xe đó
    const only = suggestions.length === 1 ? suggestions[0] : undefined;
    if (only) {
      lookup(only.plateNumber);
      return;
    }
    setError(plate ? 'Chọn một biển số gợi ý bên dưới, hoặc gõ đủ biển số (5–20 ký tự)' : 'Vui lòng nhập biển số');
  }

  return (
    <form onSubmit={submit} noValidate className="space-y-3">
      <label htmlFor="plate" className="block text-base font-semibold text-slate-700">
        Biển số xe
      </label>
      <div className="flex gap-3">
        <input
          ref={ref}
          id="plate"
          value={value}
          onChange={(event) => {
            setValue(event.target.value.toUpperCase());
            setShowSuggestions(true);
            setError(null);
            onEdit();
          }}
          placeholder="Gõ vài số cuối"
          autoFocus
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          disabled={disabled}
          aria-invalid={error ? true : undefined}
          className="min-w-0 flex-1 rounded-xl border-2 border-slate-300 bg-white px-4 py-3 font-mono text-3xl font-bold uppercase tracking-widest placeholder:font-sans placeholder:text-lg placeholder:font-normal placeholder:tracking-normal focus:border-blue-600 focus:outline-none focus:ring-4 focus:ring-blue-100 disabled:bg-slate-100"
        />
        <Button type="submit" size="xl" loading={pending} disabled={disabled}>
          Tra cứu
        </Button>
      </div>
      {error && <p className="text-base text-red-600">{error}</p>}

      {showSuggestions && suggestions.length > 0 && (
        <ul className="grid grid-cols-2 gap-2 sm:grid-cols-4" aria-label="Biển số gợi ý">
          {suggestions.map((suggestion) => (
            <li key={suggestion.plateNumber}>
              <button
                type="button"
                onClick={() => lookup(suggestion.plateNumber)}
                disabled={disabled || pending}
                className="w-full rounded-xl border-2 border-slate-200 bg-slate-50 px-3 py-2.5 text-left hover:border-blue-500 hover:bg-blue-50 disabled:opacity-50"
              >
                <span className="block font-mono text-lg font-bold tracking-wider">{suggestion.plateNumber}</span>
                <span className="block text-xs text-slate-500">
                  {typeName(suggestion.vehicleTypeCode)}
                  {!parkedOnly && suggestion.parked && <span className="ml-1 font-semibold text-amber-700">· đang trong bãi</span>}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {showSuggestions && settled && suggestions.length === 0 && (
        <p className="text-sm text-slate-500">
          {parkedOnly ? 'Không có xe nào đang trong bãi khớp với số vừa gõ.' : 'Không có biển số đã đăng ký nào khớp với số vừa gõ.'}
        </p>
      )}

      <Button variant="secondary" size="lg" className="w-full" onClick={() => setScanning(true)} disabled={disabled || pending}>
        <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10" />
        </svg>
        Quét mã QR của sinh viên
      </Button>

      {scanning && (
        <QrScannerDialog
          onClose={() => setScanning(false)}
          onResult={(plate) => {
            setScanning(false);
            lookup(plate);
          }}
        />
      )}
    </form>
  );
});
