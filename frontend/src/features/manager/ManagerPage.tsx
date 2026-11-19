import { useState } from 'react';
import { Tabs } from '../../shared/components/Tabs';
import { AccountsTab } from './AccountsTab';
import { TimesheetTab } from './TimesheetTab';
import { DepositTab } from './DepositTab';
import { GuardsTab } from './GuardsTab';
import { AdjustmentTab } from './AdjustmentTab';
import { WithdrawalRequestsTab } from './WithdrawalRequestsTab';

type Tab = 'deposit' | 'withdraw' | 'adjust' | 'guards' | 'timesheet' | 'accounts';

/** Trang quản lý bãi xe: đổi tiền mặt sang điểm, rút điểm cho chủ bãi xe, quản lý bảo vệ, quản lý tài khoản. */
export function ManagerPage() {
  const [tab, setTab] = useState<Tab>('deposit');
  return (
    <div className="space-y-6">
      <Tabs
        active={tab}
        onChange={setTab}
        tabs={[
          { id: 'deposit', label: 'Đổi điểm' },
          { id: 'withdraw', label: 'Yêu cầu rút' },
          { id: 'adjust', label: 'Điều chỉnh' },
          { id: 'guards', label: 'Bảo vệ' },
          { id: 'timesheet', label: 'Chấm công' },
          { id: 'accounts', label: 'Tài khoản' },
        ]}
      />
      {tab === 'deposit' && <DepositTab />}
      {tab === 'withdraw' && <WithdrawalRequestsTab />}
      {tab === 'adjust' && <AdjustmentTab />}
      {tab === 'guards' && <GuardsTab />}
      {tab === 'timesheet' && <TimesheetTab />}
      {tab === 'accounts' && <AccountsTab />}
    </div>
  );
}
