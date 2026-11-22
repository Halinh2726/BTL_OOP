import { useState } from 'react';
import { Tabs } from '../../shared/components/Tabs';
import { BankConfigTab } from './BankConfigTab';
import { DepositTab } from './DepositTab';
import { GuardsTab } from './GuardsTab';
import { WithdrawTab } from './WithdrawTab';

type Tab = 'deposit' | 'withdraw' | 'guards' | 'bank';

/** Trang quản lý bãi xe: đổi tiền mặt sang điểm, rút điểm cho chủ tài khoản, quản lý bảo vệ và cấu hình ngân hàng. */
export function ManagerPage() {
  const [tab, setTab] = useState<Tab>('deposit');
  return (
    <div className="space-y-6">
      <Tabs
        active={tab}
        onChange={setTab}
        tabs={[
          { id: 'deposit', label: 'Đổi điểm' },
          { id: 'withdraw', label: 'Rút điểm' },
          { id: 'guards', label: 'Bảo vệ' },
          { id: 'bank', label: 'Ngân hàng (VietQR)' },
        ]}
      />
      {tab === 'deposit' && <DepositTab />}
      {tab === 'withdraw' && <WithdrawTab />}
      {tab === 'guards' && <GuardsTab />}
      {tab === 'bank' && <BankConfigTab />}
    </div>
  );
}
