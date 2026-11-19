import { TransactionHistory } from '../../shared/components/TransactionHistory';
import { TrafficStatsCard } from '../stats/TrafficStatsCard';
import { OwnerOverviewCard } from './OwnerOverviewCard';
import { RevenueCard } from './RevenueCard';
import { WithdrawalRequestsCard } from './WithdrawalRequestsCard';

/**
 * Trang chủ bãi xe (Đợt 3): tổng quan để TỰ kiểm tra chéo số liệu, yêu cầu rút điểm, doanh thu theo ngày, lịch sử ví.
 * Chủ bãi xe không cầm tiền mặt, không sửa giao dịch, không tự duyệt yêu cầu rút của mình.
 */
export function OwnerPage() {
  return (
    <div className="space-y-6">
      <OwnerOverviewCard />
      <WithdrawalRequestsCard />
      <RevenueCard />
      <TrafficStatsCard />
      <TransactionHistory defaultType="THANH_TOAN" types={['THANH_TOAN', 'RUT_DIEM', 'DIEU_CHINH']} />
    </div>
  );
}
