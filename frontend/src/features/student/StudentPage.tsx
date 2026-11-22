import { useState } from 'react';
import { BalanceCard } from '../../shared/components/BalanceCard';
import { TransactionHistory } from '../../shared/components/TransactionHistory';
import type { ComplaintType, StudentSessionView } from '../../shared/types/api';
import { ComplaintsCard, type ComplaintPrefill } from './ComplaintsCard';
import { FeeTable } from './FeeTable';
import { MonthlyPassCard } from './MonthlyPassCard';
import { MyParkingHistoryCard } from './MyParkingHistoryCard';
import { VehiclesCard } from './VehiclesCard';

/** Trang sinh viên: số điểm, xe của tôi, vé tháng/gói ưu đãi, lịch sử gửi xe, bảng phí, lịch sử giao dịch, khiếu nại & trích xuất camera. */
export function StudentPage() {
  const [prefill, setPrefill] = useState<ComplaintPrefill | null>(null);

  function handleQuickComplaint(session: StudentSessionView, type: ComplaintType) {
    setPrefill({ sessionId: session.id, type, nonce: Date.now() });
    window.setTimeout(() => {
      document.getElementById('student-complaints')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 50);
  }

  return (
    <div className="space-y-6">
      <div className="grid items-start gap-6 md:grid-cols-2">
        <BalanceCard />
        <FeeTable />
      </div>
      <VehiclesCard />
      <MonthlyPassCard />
      <MyParkingHistoryCard onSelectSessionForComplaint={handleQuickComplaint} />
      <TransactionHistory />
      <div id="student-complaints">
        <ComplaintsCard prefill={prefill} />
      </div>
    </div>
  );
}
