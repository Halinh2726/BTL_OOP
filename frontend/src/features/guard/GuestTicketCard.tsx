import QRCode from 'qrcode';
import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import type { GuestTicket } from '../../shared/types/guest';

export function GuestTicketCard({ ticket, onNext }: { ticket: GuestTicket; onNext: () => void }) {
  const [qrData, setQrData] = useState<string | null>(null);
  const [qrFailed, setQrFailed] = useState(false);
  const typeName = useVehicleTypeName();
  const link = new URL(`/ve/${ticket.ticketToken}`, window.location.origin).href;

  useEffect(() => {
    let active = true;
    void QRCode.toDataURL(link, { width: 248, margin: 2 }).then((data) => {
      if (active) setQrData(data);
    }, () => {
      if (active) setQrFailed(true);
    });
    return () => { active = false; };
  }, [link]);

  function printTicket() {
    document.body.classList.add('printing-guest-ticket');
    try {
      window.print();
    } finally {
      document.body.classList.remove('printing-guest-ticket');
    }
  }

  const slip = (
    <>
      <p className="text-sm font-semibold text-slate-600">PTIT · Vé gửi xe khách</p>
      <h2 className="font-mono text-3xl font-bold tracking-wider text-slate-900">{ticket.ticketCode}</h2>
      <p className="text-lg font-semibold">{ticket.plateDisplay ?? 'Xe đạp không biển'}</p>
      {ticket.plateDisplay && <p className="text-sm text-slate-600">{typeName(ticket.vehicleTypeCode)}</p>}
      {qrData && <img src={qrData} width={200} height={200} alt="QR vé gửi xe khách" />}
      <p className="text-sm font-medium">Giữ mã vé để lấy xe</p>
    </>
  );

  return (
    <Card className="border-emerald-300">
      <div className="space-y-4 text-center">
        <div className="mx-auto flex max-w-sm flex-col items-center gap-2 rounded-2xl bg-white p-5 ring-1 ring-slate-200">
          {slip}
        </div>
        <p className="text-sm text-slate-600">
          {qrFailed ? 'Có thể dùng mã vé in trên thẻ để lấy xe.' : 'Mời khách chụp lại mã vé hoặc quét QR để xem phí tạm tính.'}
        </p>
        <div className="flex flex-wrap justify-center gap-2">
          <Button variant="secondary" onClick={printTicket} disabled={!qrData && !qrFailed}>In vé</Button>
          <Button onClick={onNext}>Tiếp theo</Button>
        </div>
      </div>
      {createPortal(<div className="guest-ticket-sheet">{slip}</div>, document.body)}
    </Card>
  );
}
