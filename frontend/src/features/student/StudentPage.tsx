import { BalanceCard } from '../../shared/components/BalanceCard';
import { TransactionHistory } from '../../shared/components/TransactionHistory';
import { FeeTable } from './FeeTable';
import { VehiclesCard } from './VehiclesCard';

/** Trang sinh viên: số điểm, xe của tôi, bảng phí, lịch sử giao dịch. */
export function StudentPage() {
  return (
    <div className="space-y-6">
      <div className="grid items-start gap-6 md:grid-cols-2">
        <BalanceCard />
        <FeeTable />
      </div>
      <VehiclesCard />
      <TransactionHistory />
    </div>
  );
}
