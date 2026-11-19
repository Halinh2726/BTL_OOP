import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { playFeedback, type FeedbackKind } from './feedback';

/** Tùy chọn của máy tính bảng ở cổng — lưu trong trình duyệt của máy đó, không cần lưu lên máy chủ. */
export interface GuardPreferences {
  sound: boolean;
  vibrate: boolean;
  /** Bấm "Xe tiếp theo" là camera tự mở lại. */
  continuousScan: boolean;
}

const STORAGE_KEY = 'parking-guard-preferences';
const DEFAULTS: GuardPreferences = { sound: true, vibrate: true, continuousScan: false };

function load(): GuardPreferences {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? { ...DEFAULTS, ...(JSON.parse(saved) as Partial<GuardPreferences>) } : DEFAULTS;
  } catch {
    return DEFAULTS;
  }
}

interface GuardPreferencesValue extends GuardPreferences {
  toggle: (key: keyof GuardPreferences) => void;
  feedback: (kind: FeedbackKind) => void;
}

const Context = createContext<GuardPreferencesValue | null>(null);

export function GuardPreferencesProvider({ children }: { children: ReactNode }) {
  const [preferences, setPreferences] = useState<GuardPreferences>(load);

  const toggle = useCallback((key: keyof GuardPreferences) => {
    setPreferences((current) => {
      const next = { ...current, [key]: !current[key] };
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      } catch {
        // Không lưu được (chế độ riêng tư...) thì chỉ áp dụng cho lần mở trang này
      }
      return next;
    });
  }, []);

  const feedback = useCallback(
    (kind: FeedbackKind) => playFeedback(kind, { sound: preferences.sound, vibrate: preferences.vibrate }),
    [preferences.sound, preferences.vibrate],
  );

  const value = useMemo(() => ({ ...preferences, toggle, feedback }), [preferences, toggle, feedback]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useGuardPreferences(): GuardPreferencesValue {
  const value = useContext(Context);
  if (!value) throw new Error('useGuardPreferences phải nằm trong GuardPreferencesProvider');
  return value;
}
