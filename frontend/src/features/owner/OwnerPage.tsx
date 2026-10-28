import { BalanceCard } from '../../shared/components/BalanceCard';
import { TransactionHistory } from '../../shared/components/TransactionHistory';

/** Trang chủ tài khoản: số điểm nhận được từ phí gửi xe và lịch sử (mặc định lọc khoản thanh toán). */
export function OwnerPage() {
  return (
    <div className="space-y-6">
      <BalanceCard caption="Số điểm nhận từ phí gửi xe" />
      <TransactionHistory defaultType="THANH_TOAN" types={['THANH_TOAN', 'RUT_DIEM']} />
    </div>
  );
}
