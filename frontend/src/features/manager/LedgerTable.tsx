import type { LedgerItem, LedgerParty } from '../../shared/types/api';
import { formatCash, formatDateTime, formatNumber, formatPlate, ROLE_LABELS, TRANSACTION_LABELS } from '../../shared/utils/format';

function Party({ party, fallback }: { party: LedgerParty | null; fallback: string }) {
  if (!party) return <span className="text-slate-400">{fallback}</span>;
  return (
    <span>
      {party.fullName}
      <span className="block text-xs text-slate-500">{party.studentCode ?? ROLE_LABELS[party.role]}</span>
    </span>
  );
}

/** Chi tiết riêng của từng loại: biển số (thanh toán), lý do + giao dịch gốc (điều chỉnh). */
function Detail({ item }: { item: LedgerItem }) {
  if (item.plateNumber) return <span className="font-mono">{formatPlate(item.plateNumber)}</span>;
  if (item.reason) {
    return (
      <span>
        {item.reason}
        {item.originalTransactionId && <span className="block text-xs text-slate-500">GD gốc #{item.originalTransactionId}</span>}
      </span>
    );
  }
  return <span className="text-slate-400">—</span>;
}

/** Bảng sổ giao dịch — dùng ở tab "Sổ giao dịch" và trong hồ sơ sinh viên. */
export function LedgerTable({ items, compact = false }: { items: LedgerItem[]; compact?: boolean }) {
  return (
    <div className="-mx-5 overflow-x-auto">
      <table className="min-w-full text-sm">
        <thead className="bg-slate-50 text-left text-slate-500">
          <tr>
            <th className="px-5 py-2 font-medium">Mã · Thời gian</th>
            <th className="px-3 py-2 font-medium">Loại</th>
            <th className="px-3 py-2 text-right font-medium">Điểm</th>
            {!compact && <th className="whitespace-nowrap px-3 py-2 text-right font-medium">Tiền mặt</th>}
            <th className="px-3 py-2 font-medium">Từ</th>
            <th className="px-3 py-2 font-medium">Đến</th>
            {!compact && <th className="px-3 py-2 font-medium">Người thực hiện</th>}
            <th className="px-5 py-2 font-medium">Chi tiết</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 align-top" style={{ fontVariantNumeric: 'tabular-nums' }}>
          {items.map((item) => (
            <tr key={item.id}>
              <td className="whitespace-nowrap px-5 py-2">
                <span className="font-semibold">#{item.id}</span>
                <span className="block text-xs text-slate-500">{formatDateTime(item.createdAt)}</span>
              </td>
              <td className="whitespace-nowrap px-3 py-2">{TRANSACTION_LABELS[item.type]}</td>
              <td className="px-3 py-2 text-right font-semibold">{formatNumber(item.points)}</td>
              {!compact && <td className="whitespace-nowrap px-3 py-2 text-right">{item.cashAmount === null ? '—' : formatCash(item.cashAmount)}</td>}
              <td className="px-3 py-2"><Party party={item.from} fallback="Tiền mặt vào" /></td>
              <td className="px-3 py-2"><Party party={item.to} fallback="Tiền mặt ra" /></td>
              {!compact && (
                <td className="px-3 py-2">
                  {item.performerName}
                  <span className="block text-xs text-slate-500">{ROLE_LABELS[item.performerRole]}</span>
                </td>
              )}
              <td className="px-5 py-2"><Detail item={item} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
