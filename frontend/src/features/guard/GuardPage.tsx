import { useState } from 'react';
import { Tabs } from '../../shared/components/Tabs';
import { CheckInPanel } from './CheckInPanel';
import { CheckOutPanel } from './CheckOutPanel';
import { GuardPreferencesProvider, useGuardPreferences, type GuardPreferences } from './guardPreferences';
import { IncidentsTab } from './IncidentsTab';
import { ParkedVehiclesTab } from './ParkedVehiclesTab';
import { ShiftTab } from './ShiftTab';

type Mode = 'in' | 'out' | 'lot' | 'shift' | 'incidents';

/** Màn hình bảo vệ — dùng trên máy tính bảng ở cổng: chữ to, nút to, mỗi xe một lượt thao tác. */
export function GuardPage() {
  const [mode, setMode] = useState<Mode>('in');
  return (
    <GuardPreferencesProvider>
      <div className="mx-auto max-w-3xl space-y-4">
        <Tabs
          size="lg"
          active={mode}
          onChange={setMode}
          tabs={[
            { id: 'in', label: 'Xe vào' },
            { id: 'out', label: 'Xe ra' },
            { id: 'lot', label: 'Trong bãi' },
            { id: 'shift', label: 'Ca trực' },
            { id: 'incidents', label: 'Sự cố' },
          ]}
        />
        {(mode === 'in' || mode === 'out') && <PreferenceBar />}
        {mode === 'in' && <CheckInPanel />}
        {mode === 'out' && <CheckOutPanel />}
        {mode === 'lot' && <ParkedVehiclesTab />}
        {mode === 'shift' && <ShiftTab />}
        {mode === 'incidents' && <IncidentsTab />}
      </div>
    </GuardPreferencesProvider>
  );
}

const TOGGLES: Array<[keyof GuardPreferences, string]> = [
  ['sound', 'Âm thanh'],
  ['vibrate', 'Rung'],
  ['continuousScan', 'Quét liên tục'],
];

/** Bật/tắt âm thanh, rung, quét liên tục — nhớ theo từng máy tính bảng. */
function PreferenceBar() {
  const preferences = useGuardPreferences();
  return (
    <div className="flex flex-wrap gap-2" aria-label="Tùy chọn màn hình cổng">
      {TOGGLES.map(([key, label]) => (
        <button
          key={key}
          type="button"
          aria-pressed={preferences[key]}
          onClick={() => preferences.toggle(key)}
          className={`rounded-full px-4 py-1.5 text-sm font-semibold ring-1 ring-inset ${
            preferences[key] ? 'bg-blue-700 text-white ring-blue-700' : 'bg-white text-slate-600 ring-slate-300'
          }`}
        >
          {preferences[key] ? '✓ ' : ''}
          {label}
        </button>
      ))}
    </div>
  );
}
