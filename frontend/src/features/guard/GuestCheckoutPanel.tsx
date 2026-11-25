import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { errorMessage, guestApi } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { newIdempotencyKey } from '../../shared/hooks/useIdempotencyKey';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import type { GuestCheckout, GuestLookup, GuestPaymentRoute } from '../../shared/types/guest';
import { formatCash, formatDateTime, formatDuration, formatTime, normalizePlate } from '../../shared/utils/format';
import { guestLookup } from '../../shared/utils/guestTicket';
import { QrScannerDialog } from './QrScannerDialog';
import { SuccessPanel } from './SuccessPanel';
import { VietQrPaymentCard } from './VietQrPaymentCard';

const INPUT = 'w-full rounded-xl border border-slate-300 px-3 py-2 font-mono uppercase';

export function GuestCheckoutPanel({ initialToken }: { initialToken?: string }) {
  const client = useQueryClient();
  const typeName = useVehicleTypeName();
  const [search, setSearch] = useState('');
  const [scanning, setScanning] = useState(false);
  const [observedPlate, setObservedPlate] = useState('');
  const [checkedUnplated, setCheckedUnplated] = useState(false);
  const [ticketCode, setTicketCode] = useState('');
  const [lost, setLost] = useState(false);
  const [documentLast4, setDocumentLast4] = useState('');
  const [keys, setKeys] = useState<Partial<Record<GuestPaymentRoute, string>>>({});
  const preview = useMutation({ mutationFn: guestApi.previewCheckout });
  const { mutate: previewTicket } = preview;
  const qr = useMutation({ mutationFn: ({ sessionId, ticket, observedPlate }: Pick<GuestCheckout,
    'sessionId' | 'ticket' | 'observedPlate'>) => guestApi.vietQr(sessionId, { ticket, observedPlate }) });
  const payment = useMutation({
    mutationFn: ({ route, body, key }: { route: GuestPaymentRoute; body: GuestCheckout; key: string }) =>
      guestApi.checkout(route, body, key),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['guard'] });
      void client.invalidateQueries({ queryKey: ['guest'] });
    },
  });

  useEffect(() => {
    if (initialToken) previewTicket({ ticketToken: initialToken });
  }, [initialToken, previewTicket]);

  function reset() {
    preview.reset();
    payment.reset();
    qr.reset();
    setObservedPlate('');
    setCheckedUnplated(false);
    setTicketCode('');
    setDocumentLast4('');
    setLost(false);
    setKeys({});
  }

  function lookup(ticket: GuestLookup) {
    reset();
    previewTicket(ticket);
  }

  const data = preview.data;
  const selected = preview.variables;
  const proof = selected?.ticketToken || selected?.ticketCode ? selected : { ticketCode: ticketCode.trim() };
  const hasProof = Boolean(proof?.ticketToken || /^K-\d{6}-\d{4}$/i.test(proof?.ticketCode ?? ''));
  const matchesVehicle = data && (data.plateNumber
    ? normalizePlate(observedPlate) === data.plateNumber : checkedUnplated);
  const approvedLost = Boolean(data && (data.plateNumber || data.lostTicketApproved));
  const ready = matchesVehicle && (lost ? approvedLost && /^[0-9]{4}$/.test(documentLast4) : hasProof);
  const amount = data ? data.quote.amount + (lost ? data.lostTicketFeeVnd : 0) : 0;
  const busy = payment.isPending || qr.isPending;

  function pay(route: GuestPaymentRoute, expectedAmount: number, quoteId?: string) {
    if (!data || !ready || busy) return;
    const key = keys[route] ?? newIdempotencyKey();
    setKeys({ ...keys, [route]: key });
    payment.mutate({ route, key, body: {
      sessionId: data.sessionId,
      ticket: lost ? undefined : proof,
      observedPlate: data.plateNumber ? observedPlate : undefined,
      documentLast4: lost ? documentLast4 : undefined,
      expectedAmount,
      quoteId,
    } });
  }

  if (payment.data) {
    const receipt = payment.data.data;
    return (
      <SuccessPanel title="Đã thu tiền và cho xe khách ra" onNext={() => { reset(); setSearch(''); }}
        notice={payment.data.replayed && <Alert tone="info">Khoản thu này đã được ghi nhận trước đó.</Alert>}>
        <InfoRow label="Mã vé" value={receipt.ticketCode} />
        <InfoRow label="Phương thức" value={receipt.method === 'TIEN_MAT' ? 'Tiền mặt' : 'VietQR'} />
        <InfoRow label="Thực thu" value={formatCash(receipt.amount)} />
        {receipt.lostTicket && <InfoRow label="Phí mất vé" value={formatCash(receipt.lostTicketFee)} />}
        <InfoRow label="Giờ ra" value={formatDateTime(receipt.paidAt)} />
      </SuccessPanel>
    );
  }

  return (
    <div className="space-y-4">
      <Card title="Tra vé xe khách">
        <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); lookup(guestLookup(search)); }}>
          <label className="block space-y-1 font-medium">
            Mã vé hoặc biển số
            <input className={INPUT} value={search} required maxLength={180} autoComplete="off"
              placeholder="K-YYMMDD-NNNN" disabled={busy}
              onChange={(event) => { reset(); setSearch(event.target.value); }} />
          </label>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" loading={preview.isPending} disabled={busy}>Tra vé</Button>
            <Button variant="secondary" disabled={busy} onClick={() => setScanning(true)}>Quét QR vé</Button>
          </div>
        </form>
        {preview.isError && <Alert tone="error">{errorMessage(preview.error)}</Alert>}
      </Card>
      {scanning && (
        <QrScannerDialog onResult={() => undefined} guestOnly onClose={() => setScanning(false)}
          onGuestToken={(token) => { setScanning(false); lookup({ ticketToken: token }); }} />
      )}
      {data && (
        <Card title="Đối chiếu xe trước khi cho ra">
          <div className="space-y-4">
            <dl>
              <InfoRow label="Biển số trên vé" value={<strong className="font-mono text-2xl">{data.plateDisplay ?? 'Không biển'}</strong>} />
              <InfoRow label="Loại xe" value={typeName(data.vehicleTypeCode)} />
              <InfoRow label="Giờ vào" value={formatDateTime(data.enteredAt)} />
              <InfoRow label="Thời gian gửi" value={formatDuration(data.quote.minutesParked)} />
              <InfoRow label={data.quote.overnight ? 'Phí qua đêm' : 'Phí gửi'} value={formatCash(data.quote.amount)} />
            </dl>
            {data.quote.checkoutLocked ? (
              <Alert tone="warning">Đã đến giờ khóa lấy xe ({data.quote.checkoutCurfewTime?.slice(0, 5)}).</Alert>
            ) : (
              <fieldset className="space-y-3" disabled={busy || Boolean(qr.data)}>
                {data.plateNumber ? (
                  <label className="block space-y-1 font-medium">Biển số thực tế
                    <input className={INPUT} value={observedPlate} maxLength={32}
                      onChange={(event) => setObservedPlate(event.target.value)} autoComplete="off" />
                  </label>
                ) : (
                  <label className="flex gap-2">
                    <input type="checkbox" checked={checkedUnplated} onChange={(event) => setCheckedUnplated(event.target.checked)} />
                    Đã đối chiếu xe đạp không biển
                  </label>
                )}
                <label className="flex gap-2">
                  <input type="checkbox" checked={lost} onChange={(event) => { payment.reset(); setLost(event.target.checked); }} />
                  Khách mất vé
                </label>
                {lost && !approvedLost && (
                  <Alert tone="warning">Nhờ Quản lý đối chiếu và duyệt mất vé xe đạp không biển. Sau đó tra lại vé.</Alert>
                )}
                {lost && approvedLost && (
                  <>
                    <label className="block space-y-1 font-medium">4 số cuối giấy tờ đã đối chiếu
                      <input className={INPUT} value={documentLast4} onChange={(event) => setDocumentLast4(event.target.value.replace(/\D/g, '').slice(0, 4))}
                        inputMode="numeric" maxLength={4} autoComplete="off" />
                    </label>
                    <p className="text-sm">Phí mất vé {formatCash(data.lostTicketFeeVnd)}</p>
                  </>
                )}
                {!lost && selected?.plateNumber && (
                  <label className="block space-y-1 font-medium">Mã trên vé khách xuất trình
                    <input className={INPUT} value={ticketCode} onChange={(event) => setTicketCode(event.target.value)} maxLength={20} />
                  </label>
                )}
                {!qr.data && (
                  <div className="flex flex-wrap gap-2">
                    <Button variant="success" disabled={!ready} onClick={() => pay(lost ? 'lost-ticket' : 'cash', amount)}>
                      Đã thu tiền mặt · {formatCash(amount)}
                    </Button>
                    {!lost && <Button variant="secondary" disabled={!ready} onClick={() => {
                      payment.reset();
                      qr.mutate({ sessionId: data.sessionId, ticket: proof, observedPlate: data.plateNumber ? observedPlate : undefined });
                    }}>Mở VietQR</Button>}
                  </div>
                )}
              </fieldset>
            )}
            {qr.isError && <Alert tone="error">{errorMessage(qr.error)}</Alert>}
            {payment.isError && !qr.data && <Alert tone="error">{errorMessage(payment.error)}</Alert>}
          </div>
        </Card>
      )}
      {qr.data && (
        <VietQrPaymentCard order={qr.data.order} guest confirming={payment.isPending}
          expiresAt={formatTime(qr.data.expiresAt)}
          error={payment.isError ? errorMessage(payment.error) : null}
          onCancel={() => { payment.reset(); qr.reset(); }}
          onConfirm={() => { if (qr.data) pay('vietqr', qr.data.order.fee, qr.data.quoteId); }} />
      )}
    </div>
  );
}
