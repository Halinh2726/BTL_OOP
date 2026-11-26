import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { errorMessage, guestApi } from '../../shared/api';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { newIdempotencyKey } from '../../shared/hooks/useIdempotencyKey';
import { useVehicleTypeName } from '../../shared/hooks/useVehicleTypeName';
import type { GuestCheckout, GuestLookup, GuestPaymentRoute } from '../../shared/types/guest';
import { formatCash, formatTime } from '../../shared/utils/format';
import { guestLookup } from '../../shared/utils/guestTicket';
import { QrScannerDialog } from './QrScannerDialog';
import { ReportIncidentDialog } from './ReportIncidentDialog';
import { SuccessPanel } from './SuccessPanel';
import { VietQrPaymentCard } from './VietQrPaymentCard';

const INPUT = 'w-full rounded-xl border border-slate-300 px-3 py-2 font-mono uppercase';

export function GuestCheckoutPanel({ initialToken }: { initialToken?: string }) {
  const client = useQueryClient();
  const typeName = useVehicleTypeName();
  const searchRef = useRef<HTMLInputElement>(null);
  const [search, setSearch] = useState('');
  const [scanning, setScanning] = useState(false);
  const [ticketCode, setTicketCode] = useState('');
  const [lost, setLost] = useState(false);
  const [documentLast4, setDocumentLast4] = useState('');
  const [keys, setKeys] = useState<Partial<Record<GuestPaymentRoute, string>>>({});
  const [reportingMismatch, setReportingMismatch] = useState(false);
  const [mismatchReported, setMismatchReported] = useState(false);
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
    setTicketCode('');
    setDocumentLast4('');
    setLost(false);
    setKeys({});
    setReportingMismatch(false);
    setMismatchReported(false);
  }

  function lookup(ticket: GuestLookup) {
    reset();
    previewTicket(ticket);
  }

  const data = preview.data;
  const selected = preview.variables;
  const proof = selected?.ticketToken || selected?.ticketCode ? selected : { ticketCode: ticketCode.trim() };
  const hasProof = Boolean(proof?.ticketToken || /^K-\d{6}-\d{4}$/i.test(proof?.ticketCode ?? ''));
  const approvedLost = Boolean(data && (data.plateNumber || data.lostTicketApproved));
  const ready = Boolean(data) && (lost ? approvedLost && /^[0-9]{4}$/.test(documentLast4) : hasProof);
  const amount = data ? data.quote.amount + (lost ? data.lostTicketFeeVnd : 0) : 0;
  const busy = payment.isPending || qr.isPending;

  function pay(route: GuestPaymentRoute, expectedAmount: number, quoteId?: string) {
    if (!data || !ready || busy) return;
    const key = keys[route] ?? newIdempotencyKey();
    setKeys({ ...keys, [route]: key });
    payment.mutate({ route, key, body: {
      sessionId: data.sessionId,
      ticket: lost ? undefined : proof,
      observedPlate: data.plateNumber ?? undefined,
      documentLast4: lost ? documentLast4 : undefined,
      expectedAmount,
      quoteId,
    } });
  }

  if (payment.data) {
    const receipt = payment.data.data;
    return (
      <SuccessPanel
        title="Đã thu tiền và cho xe khách ra"
        onNext={() => {
          reset();
          setSearch('');
          setTimeout(() => searchRef.current?.focus(), 0);
        }}
        notice={payment.data.replayed && <Alert tone="info">Khoản thu này đã được ghi nhận trước đó.</Alert>}
      >
        <InfoRow label="Mã vé" value={receipt.ticketCode} />
        <InfoRow label="Phương thức" value={receipt.method === 'TIEN_MAT' ? 'Tiền mặt' : 'VietQR'} />
        <InfoRow label="Thực thu" value={formatCash(receipt.amount)} />
        {receipt.lostTicket && <InfoRow label="Phí mất vé" value={formatCash(receipt.lostTicketFee)} />}
      </SuccessPanel>
    );
  }

  const plateLabel = data?.plateDisplay ?? 'Xe đạp (Không biển)';
  const cashButtonLabel = lost
    ? `Xác nhận mất vé · Thu ${formatCash(amount)}`
    : data?.plateDisplay
      ? `Khớp biển ${data.plateDisplay} · Thu tiền mặt (${formatCash(amount)})`
      : `Khớp xe đạp · Thu tiền mặt (${formatCash(amount)})`;

  return (
    <div className="space-y-4">
      <Card title="Tra vé xe khách">
        <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); lookup(guestLookup(search)); }}>
          <label className="block space-y-1 font-medium">
            Mã vé hoặc biển số
            <input ref={searchRef} className={INPUT} value={search} required maxLength={180} autoComplete="off"
              placeholder="K-YYMMDD-NNNN hoặc biển số" disabled={busy}
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
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 text-center">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Đối chiếu biển số xe thực tế với vé
              </p>
              <div className="mt-2 inline-block rounded-xl border-2 border-slate-900 bg-white px-5 py-2 font-mono text-3xl font-extrabold tracking-wider text-slate-900 shadow-sm sm:text-4xl">
                {plateLabel}
              </div>
              <p className="mt-2 text-sm text-slate-600">
                {typeName(data.vehicleTypeCode)} · <strong className="text-slate-900">{formatCash(amount)}</strong>
              </p>
            </div>
            <dl>
              <InfoRow label={data.quote.overnight ? 'Phí qua đêm' : 'Phí gửi'} value={formatCash(data.quote.amount)} />
            </dl>
            {mismatchReported && (
              <Alert tone="warning">
                Đã gửi báo cáo sự cố không khớp biển số cho Quản lý. Vui lòng giữ xe chờ xử lý.
              </Alert>
            )}
            {data.quote.checkoutLocked ? (
              <Alert tone="warning">Đã đến giờ khóa lấy xe ({data.quote.checkoutCurfewTime?.slice(0, 5)}).</Alert>
            ) : (
              <fieldset className="space-y-3" disabled={busy || Boolean(qr.data)}>
                {!lost && selected?.plateNumber && (
                  <div className="space-y-1">
                    <label className="block font-medium">Mã trên vé khách xuất trình</label>
                    <div className="flex flex-wrap gap-2">
                      <input className={`${INPUT} min-w-0 flex-1`} value={ticketCode}
                        placeholder="K-YYMMDD-NNNN"
                        onChange={(event) => setTicketCode(event.target.value)} maxLength={20} />
                      <Button variant="secondary" type="button" onClick={() => setScanning(true)}>
                        Quét QR vé
                      </Button>
                    </div>
                  </div>
                )}
                <label className="flex items-center gap-2 text-sm font-medium text-slate-700">
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
                    <p className="text-sm text-slate-600">Phí mất vé: <strong>{formatCash(data.lostTicketFeeVnd)}</strong></p>
                  </>
                )}
                {!qr.data && (
                  <div className="flex flex-wrap items-center gap-2 pt-1">
                    <Button variant="success" size="lg" disabled={!ready} onClick={() => pay(lost ? 'lost-ticket' : 'cash', amount)}>
                      {cashButtonLabel}
                    </Button>
                    {!lost && (
                      <Button variant="secondary" size="lg" disabled={!ready} onClick={() => {
                        payment.reset();
                        qr.mutate({ sessionId: data.sessionId, ticket: proof, observedPlate: data.plateNumber ?? undefined });
                      }}>
                        Mở VietQR · {formatCash(amount)}
                      </Button>
                    )}
                    {!lost && (
                      <Button variant="danger" size="sm" type="button" onClick={() => setReportingMismatch(true)}>
                        Không khớp biển số
                      </Button>
                    )}
                  </div>
                )}
              </fieldset>
            )}
            {qr.isError && <Alert tone="error">{errorMessage(qr.error)}</Alert>}
            {payment.isError && !qr.data && <Alert tone="error">{errorMessage(payment.error)}</Alert>}
          </div>
        </Card>
      )}
      {reportingMismatch && data && (
        <ReportIncidentDialog
          preset={{
            type: 'KHAC',
            plateNumber: data.plateNumber ?? '',
            description: `Xe khách lấy xe không khớp với biển số trên vé (${plateLabel}).`,
          }}
          onClose={() => setReportingMismatch(false)}
          onReported={() => {
            setReportingMismatch(false);
            setMismatchReported(true);
          }}
        />
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
