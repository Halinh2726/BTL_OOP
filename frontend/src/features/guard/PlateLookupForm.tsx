import { forwardRef, useState, type FormEvent } from 'react';
import { Button } from '../../shared/components/Button';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import { normalizePlate } from '../../shared/utils/format';
import { QrScannerDialog } from './QrScannerDialog';
import { usePlateSuggestions } from './usePlateSuggestions';

const PLATE = /^[A-Z0-9]{5,20}$/;

interface PlateLookupFormProps {
  onLookup: (plate: string) => void;
  /** Callback khi quét mã ở chế độ 1 chạm (tự động vào bãi hoặc tự động trừ điểm). */
  onQuickScan?: (plate: string) => void;
  quickScanLabel?: string;
  pending: boolean;
  disabled?: boolean;
  /** Màn hình xe ra: chỉ gợi ý xe đang trong bãi. */
  parkedOnly: boolean;
  /** Bảo vệ bắt đầu gõ biển số khác -> bỏ kết quả tra cứu của xe trước để không nhìn nhầm. */
  onEdit: () => void;
}

/**
 * Các cách nhập biển số cho bảo vệ:
 * 1. Quét mã QR 1 chạm (như vé xe bus / thẻ tàu điện)
 * 2. Quét QR để tra cứu xem trước
 * 3. Gõ biển số / số cuối để tra cứu gợi ý
 */
export const PlateLookupForm = forwardRef<HTMLInputElement, PlateLookupFormProps>(function PlateLookupForm(
  { onLookup, onQuickScan, quickScanLabel, pending, disabled = false, parkedOnly, onEdit },
  ref,
) {
  const [value, setValue] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [showSuggestions, setShowSuggestions] = useState(true);
  const [scanning, setScanning] = useState(false);
  const [scanMode, setScanMode] = useState<'quick' | 'lookup'>('quick');
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
    <form onSubmit={submit} noValidate className="space-y-4">
      {/* Khối nút quét mã QR 1 chạm */}
      {onQuickScan ? (
        <div className="space-y-2 rounded-xl bg-slate-50 p-3 ring-1 ring-slate-200">
          <Button
            type="button"
            variant="success"
            size="xl"
            className="w-full flex items-center justify-center gap-2 font-bold tracking-wide shadow-sm"
            onClick={() => {
              setScanMode('quick');
              setScanning(true);
            }}
            disabled={disabled || pending}
          >
            <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
              <path d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
            {quickScanLabel ?? 'Quét 1 chạm (Tự động)'}
          </Button>

          <Button
            type="button"
            variant="secondary"
            size="md"
            className="w-full text-slate-600"
            onClick={() => {
              setScanMode('lookup');
              setScanning(true);
            }}
            disabled={disabled || pending}
          >
            <svg viewBox="0 0 24 24" className="mr-1.5 h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <path d="M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10" />
            </svg>
            Quét để tra cứu / xem trước
          </Button>
        </div>
      ) : (
        <Button
          type="button"
          variant="secondary"
          size="lg"
          className="w-full"
          onClick={() => {
            setScanMode('lookup');
            setScanning(true);
          }}
          disabled={disabled || pending}
        >
          <svg viewBox="0 0 24 24" className="mr-2 h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path d="M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10" />
          </svg>
          Quét mã QR của sinh viên
        </Button>
      )}

      {/* Ô nhập biển số thủ công */}
      <div className="space-y-1">
        <label htmlFor="plate" className="block text-sm font-medium text-slate-600">
          Hoặc nhập biển số thủ công
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

      {scanning && (
        <QrScannerDialog
          onClose={() => setScanning(false)}
          onResult={(plate) => {
            setScanning(false);
            if (scanMode === 'quick' && onQuickScan) {
              onQuickScan(plate);
            } else {
              lookup(plate);
            }
          }}
        />
      )}
    </form>
  );
});
