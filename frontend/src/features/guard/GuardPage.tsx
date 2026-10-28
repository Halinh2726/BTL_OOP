import { useState } from 'react';
import { Tabs } from '../../shared/components/Tabs';
import { CheckInPanel } from './CheckInPanel';
import { CheckOutPanel } from './CheckOutPanel';

type Mode = 'in' | 'out';

/** Màn hình bảo vệ — dùng trên máy tính bảng ở cổng: chữ to, nút to, mỗi xe một lượt thao tác. */
export function GuardPage() {
  const [mode, setMode] = useState<Mode>('in');
  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Tabs
        size="lg"
        active={mode}
        onChange={setMode}
        tabs={[
          { id: 'in', label: 'Xe vào' },
          { id: 'out', label: 'Xe ra' },
        ]}
      />
      {mode === 'in' ? <CheckInPanel /> : <CheckOutPanel />}
    </div>
  );
}
