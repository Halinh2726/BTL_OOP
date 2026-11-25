import { useState, type ReactNode } from 'react';
import { Tabs } from '../../shared/components/Tabs';
import { TrafficStatsCard } from '../stats/TrafficStatsCard';
import { AccountsTab } from './AccountsTab';
import { AdjustmentTab } from './AdjustmentTab';
import { BankConfigTab } from './BankConfigTab';
import { ComplaintsTab } from './ComplaintsTab';
import { IncidentsQueueTab } from './IncidentsQueueTab';
import { ShiftPlanTab } from './ShiftPlanTab';
import { DepositTab } from './DepositTab';
import { FeeCatalogTab } from './FeeCatalogTab';
import { GuardsTab } from './GuardsTab';
import { LedgerTab } from './LedgerTab';
import { PassCatalogTab } from './PassCatalogTab';
import { ReconciliationTab } from './ReconciliationTab';
import { SmtpConfigTab } from './SmtpConfigTab';
import { SocialLoginConfigTab } from './SocialLoginConfigTab';
import { StudentsTab } from './StudentsTab';
import { TimesheetTab } from './TimesheetTab';
import { WithdrawalRequestsTab } from './WithdrawalRequestsTab';

type Group = 'cashier' | 'cases' | 'lookup' | 'stats' | 'staff' | 'settings';

interface Section {
  id: string;
  label: string;
  render: () => ReactNode;
}

/** Mỗi nhóm nhớ tab con đang mở khi chuyển qua lại. */
const GROUPS: Record<Group, { label: string; sections: Section[] }> = {
  cashier: {
    label: 'Thu ngân',
    sections: [
      { id: 'deposit', label: 'Đổi điểm', render: () => <DepositTab /> },
      { id: 'fee-catalog', label: 'Giá vé lượt', render: () => <FeeCatalogTab /> },
      { id: 'pass-catalog', label: 'Giá vé tháng', render: () => <PassCatalogTab /> },
      { id: 'withdraw', label: 'Yêu cầu rút', render: () => <WithdrawalRequestsTab /> },
      { id: 'adjust', label: 'Điều chỉnh', render: () => <AdjustmentTab /> },
      { id: 'reconcile', label: 'Đối soát cuối ngày', render: () => <ReconciliationTab /> },
      { id: 'bank', label: 'Ngân hàng & Giờ khóa', render: () => <BankConfigTab /> },
    ],
  },
  cases: {
    label: 'Xử lý',
    sections: [
      { id: 'complaints', label: 'Khiếu nại sinh viên', render: () => <ComplaintsTab /> },
      { id: 'incidents', label: 'Sự cố bảo vệ báo', render: () => <IncidentsQueueTab /> },
    ],
  },
  lookup: {
    label: 'Tra cứu',
    sections: [
      { id: 'students', label: 'Sinh viên', render: () => <StudentsTab /> },
      { id: 'ledger', label: 'Sổ giao dịch', render: () => <LedgerTab /> },
    ],
  },
  stats: {
    label: 'Thống kê',
    sections: [{ id: 'traffic', label: 'Lượt xe theo giờ', render: () => <TrafficStatsCard /> }],
  },
  staff: {
    label: 'Nhân sự',
    sections: [
      { id: 'guards', label: 'Bảo vệ', render: () => <GuardsTab /> },
      { id: 'shiftplan', label: 'Phân ca', render: () => <ShiftPlanTab /> },
      { id: 'timesheet', label: 'Chấm công', render: () => <TimesheetTab /> },
      { id: 'accounts', label: 'Tài khoản', render: () => <AccountsTab /> },
    ],
  },
  settings: {
    label: 'Cấu hình',
    sections: [
      { id: 'smtp', label: 'Email (SMTP)', render: () => <SmtpConfigTab /> },
      { id: 'social-login', label: 'Đăng nhập Google / Microsoft', render: () => <SocialLoginConfigTab /> },
    ],
  },
};

export function ManagerPage() {
  const [group, setGroup] = useState<Group>('cashier');
  const [openSection, setOpenSection] = useState<Record<Group, string>>({
    cashier: 'deposit', cases: 'complaints', lookup: 'students', stats: 'traffic', staff: 'guards', settings: 'smtp',
  });
  const sections = GROUPS[group].sections;
  const current = sections.find((section) => section.id === openSection[group]) ?? sections[0];

  return (
    <div className="space-y-6">
      <Tabs
        active={group}
        onChange={setGroup}
        wrap
        tabs={(Object.keys(GROUPS) as Group[]).map((id) => ({ id, label: GROUPS[id].label }))}
      />
      {sections.length > 1 && (
        <div role="tablist" aria-label={GROUPS[group].label} className="flex flex-wrap gap-2">
          {sections.map((section) => (
            <button
              key={section.id}
              type="button"
              role="tab"
              aria-selected={section.id === current?.id}
              onClick={() => setOpenSection((previous) => ({ ...previous, [group]: section.id }))}
              className={`rounded-full px-4 py-1.5 text-sm font-semibold ring-1 ring-inset transition-colors ${
                section.id === current?.id ? 'bg-blue-700 text-white ring-blue-700' : 'bg-white text-slate-600 ring-slate-300 hover:bg-slate-50'
              }`}
            >
              {section.label}
            </button>
          ))}
        </div>
      )}
      {current?.render()}
    </div>
  );
}
