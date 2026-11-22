import { forwardRef, useId, useState, type ChangeEvent, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes } from 'react';

const CONTROL =
  'block w-full rounded-lg border bg-white px-3 py-2 text-slate-900 shadow-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 disabled:bg-slate-100';

function controlClass(error: string | undefined, className: string): string {
  const state = error
    ? 'border-red-400 focus:border-red-500 focus:ring-red-200'
    : 'border-slate-300 focus:border-blue-600 focus:ring-blue-200';
  return `${CONTROL} ${state} ${className}`;
}

interface FieldShellProps {
  id: string;
  label: string;
  error?: string;
  hint?: ReactNode;
  children: ReactNode;
}

function FieldShell({ id, label, error, hint, children }: FieldShellProps) {
  return (
    <div className="space-y-1">
      <label htmlFor={id} className="block text-sm font-medium text-slate-700">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} className="text-sm text-red-600">
          {error}
        </p>
      ) : (
        hint && <p className="text-sm text-slate-500">{hint}</p>
      )}
    </div>
  );
}

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
  hint?: ReactNode;
}

/** Ô nhập có nhãn và dòng lỗi bên dưới — dùng với register() của react-hook-form. */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, hint, className = '', id, ...rest },
  ref,
) {
  const generated = useId();
  const inputId = id ?? generated;
  return (
    <FieldShell id={inputId} label={label} error={error} hint={hint}>
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${inputId}-error` : undefined}
        className={controlClass(error, className)}
        {...rest}
      />
    </FieldShell>
  );
});

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  error?: string;
  children: ReactNode;
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, error, className = '', id, children, ...rest },
  ref,
) {
  const generated = useId();
  const selectId = id ?? generated;
  return (
    <FieldShell id={selectId} label={label} error={error}>
      <select
        ref={ref}
        id={selectId}
        aria-invalid={error ? true : undefined}
        className={controlClass(error, className)}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  );
});

interface PasswordFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  error?: string;
  hint?: ReactNode;
  showStrength?: boolean;
}

function passwordStrength(value: string): { level: 1 | 2 | 3; label: string; color: string; width: string } {
  let score = 0;
  if (value.length >= 8) score += 1;
  if (value.length >= 12) score += 1;
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score += 1;
  if (/\d/.test(value)) score += 1;
  if (/[^A-Za-z0-9]/.test(value)) score += 1;
  if (score >= 4) return { level: 3, label: 'Mạnh', color: 'bg-emerald-600 text-emerald-700', width: 'w-full' };
  if (score >= 3) return { level: 2, label: 'Trung bình', color: 'bg-amber-500 text-amber-700', width: 'w-2/3' };
  return { level: 1, label: 'Yếu (nên gồm chữ hoa, thường và số)', color: 'bg-red-500 text-red-700', width: 'w-1/3' };
}

/** Ô mật khẩu có nút bật/tắt hiển thị và thanh đánh giá độ mạnh tùy chọn. */
export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(function PasswordField(
  { label, error, hint, showStrength = false, className = '', id, onChange, value, ...rest },
  ref,
) {
  const generated = useId();
  const inputId = id ?? generated;
  const [visible, setVisible] = useState(false);
  const [draft, setDraft] = useState('');
  const currentValue = typeof value === 'string' ? value : draft;
  const strength = showStrength && currentValue.length > 0 ? passwordStrength(currentValue) : null;

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    setDraft(event.target.value);
    onChange?.(event);
  }

  return (
    <FieldShell id={inputId} label={label} error={error} hint={hint}>
      <div className="relative">
        <input
          ref={ref}
          id={inputId}
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={handleChange}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : undefined}
          className={controlClass(error, `pr-11 ${className}`)}
          {...rest}
        />
        <button
          type="button"
          onClick={() => setVisible((prev) => !prev)}
          aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
          className="absolute inset-y-0 right-0 flex items-center px-3 text-slate-500 hover:text-slate-800 focus:outline-none"
        >
          {visible ? (
            <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M17.94 17.94A10.94 10.94 0 0 1 12 20C7 20 2.73 16.89 1 12c.9-2.55 2.52-4.68 4.6-6.11M9.9 4.24A10.94 10.94 0 0 1 12 4c5 0 9.27 3.11 11 8a10.94 10.94 0 0 1-4.18 5.29" />
              <path d="M1 1l22 22" />
            </svg>
          ) : (
            <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          )}
        </button>
      </div>
      {strength && (
        <div className="space-y-1 pt-0.5">
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-200">
            <div className={`h-full transition-all ${strength.width} ${strength.color.split(' ')[0]}`} />
          </div>
          <p className={`text-xs font-medium ${strength.color.split(' ')[1]}`}>Độ mạnh: {strength.label}</p>
        </div>
      )}
    </FieldShell>
  );
});
