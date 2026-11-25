import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useReducer, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { errorMessage } from '../../../shared/api';
import { Button } from '../../../shared/components/Button';
import { Card } from '../../../shared/components/Card';
import { newIdempotencyKey } from '../../../shared/hooks/useIdempotencyKey';
import { useVehicleTypeName } from '../../../shared/hooks/useVehicleTypeName';
import { formatCash, formatPlate, formatPoints, formatTime, normalizePlate, REASON_LABELS } from '../../../shared/utils/format';
import { plateFromQr } from '../../../shared/utils/plateQr';
import { guestTokenFromQr } from '../../../shared/utils/guestTicket';
import { useGuardPreferences } from '../guardPreferences';
import { usePlateSuggestions } from '../usePlateSuggestions';
import { useQrCamera } from '../useQrCamera';
import type { GateFlow } from './gateFlows';
import { COUNTDOWN_MS, INITIAL, RecentPlates, autoNextDelay, reduce, type ScanState, type Verdict } from './scanMachine';

const TICK_MS = 100;
const HISTORY_SIZE = 8;

interface HistoryItem {
  plateDisplay: string;
  ok: boolean;
  text: string;
  at: string;
}

/**
 * Quét liên tục: camera mở suốt; quét được mã → tự tra cứu → được qua thì đếm ngược 2 giây rồi TỰ xác nhận,
 * bị từ chối thì báo đỏ → tự quay lại quét xe tiếp theo. Bảo vệ không phải chạm màn hình, chỉ chạm "Dừng" khi
 * thấy tên / biển số không khớp. Luồng xe vào hay xe ra được truyền vào qua {@link GateFlow}.
 */
export function ContinuousScanPanel({ flow, onGuestToken }: { flow: GateFlow; onGuestToken?: (token: string) => void }) {
  const queryClient = useQueryClient();
  const { feedback } = useGuardPreferences();
  const typeName = useVehicleTypeName();
  const [state, dispatch] = useReducer(reduce, INITIAL);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [typed, setTyped] = useState('');
  const [foreignCode, setForeignCode] = useState(false);
  const recent = useRef(new RecentPlates());
  const { suggestions, settled } = usePlateSuggestions(typed, flow.parkedOnly, state.kind === 'scanning');
  const videoRef = useRef<HTMLVideoElement>(null);

  function detect(plate: string) {
    if (state.kind !== 'scanning' || recent.current.isCoolingDown(plate, Date.now())) return;
    setForeignCode(false);
    dispatch({ type: 'DETECTED', plate });
  }

  const { error: cameraError } = useQrCamera(
    videoRef,
    (text) => {
      const guestToken = onGuestToken ? guestTokenFromQr(text) : null;
      if (guestToken && onGuestToken) {
        onGuestToken(guestToken);
        return;
      }
      const plate = plateFromQr(text);
      if (plate) detect(plate);
      else setForeignCode(true);
    },
    state.kind !== 'scanning',
  );

  // Tra cứu ngay khi quét được biển số
  useEffect(() => {
    if (state.kind !== 'checking') return;
    let active = true;
    flow
      .preview(state.plate)
      .then((verdict) => active && dispatch({ type: 'PREVIEWED', verdict, key: newIdempotencyKey() }))
      .catch((error: unknown) => active && dispatch({ type: 'FAILED', message: errorMessage(error) }));
    return () => {
      active = false;
    };
  }, [state, flow]);

  // Đếm ngược
  useEffect(() => {
    if (state.kind !== 'countdown') return;
    const timer = window.setTimeout(() => dispatch({ type: 'TICK', elapsedMs: TICK_MS }), TICK_MS);
    return () => window.clearTimeout(timer);
  }, [state]);

  // Tự xác nhận khi hết đếm ngược
  useEffect(() => {
    if (state.kind !== 'confirming') return;
    let active = true;
    flow
      .confirm(state.verdict, state.key)
      .then((outcome) => {
        if (!active) return;
        dispatch({ type: 'CONFIRMED', outcome });
        void queryClient.invalidateQueries({ queryKey: ['guard'] });
      })
      .catch((error: unknown) => active && dispatch({ type: 'FAILED', message: error instanceof Error && !('code' in error) ? error.message : errorMessage(error) }));
    return () => {
      active = false;
    };
  }, [state, flow, queryClient]);

  // Âm thanh + lịch sử + chống xử lý lặp khi đổi trạng thái
  const previous = useRef<ScanState['kind']>(state.kind);
  useEffect(() => {
    if (previous.current === state.kind) return;
    previous.current = state.kind;
    const now = new Date().toISOString();
    const add = (verdict: Verdict | undefined, plate: string, ok: boolean, text: string) =>
      setHistory((items) => [{ plateDisplay: verdict?.plateDisplay ?? plate, ok, text, at: now }, ...items].slice(0, HISTORY_SIZE));
    switch (state.kind) {
      case 'countdown':
        feedback('allow');
        break;
      case 'denied':
        feedback('deny');
        recent.current.mark(state.verdict.plate, Date.now());
        add(state.verdict, state.verdict.plate, false, reasonText(state.verdict));
        break;
      case 'done':
        feedback('done');
        recent.current.mark(state.verdict.plate, Date.now());
        add(state.verdict, state.verdict.plate, true, state.outcome.title);
        break;
      case 'stopped':
        recent.current.mark(state.verdict.plate, Date.now());
        add(state.verdict, state.verdict.plate, false, 'Bảo vệ dừng lại');
        break;
      case 'failed':
        feedback('deny');
        recent.current.mark(state.plate, Date.now());
        break;
      default:
        break;
    }
  }, [state, feedback]);

  // Tự quay lại quét xe tiếp theo
  useEffect(() => {
    const delay = autoNextDelay(state);
    if (delay === null) return;
    const timer = window.setTimeout(() => dispatch({ type: 'NEXT' }), delay);
    return () => window.clearTimeout(timer);
  }, [state]);

  function submitTyped(event: FormEvent) {
    event.preventDefault();
    const plate = normalizePlate(typed);
    if (/^[A-Z0-9]{5,20}$/.test(plate)) {
      pick(plate);
      return;
    }
    // Gõ mấy số cuối mà chỉ khớp đúng một xe -> Enter chọn luôn xe đó
    const only = suggestions.length === 1 ? suggestions[0] : undefined;
    if (only) pick(only.plateNumber);
  }

  function pick(plate: string) {
    setTyped('');
    detect(plate);
  }

  return (
    <div className="space-y-4">
      <Card>
        <div className="grid gap-4 md:grid-cols-[1fr_1.2fr]">
          <div className="space-y-2">
            {cameraError ? (
              <p className="rounded-xl bg-red-50 p-4 text-red-800">{cameraError}</p>
            ) : (
              <div className="relative overflow-hidden rounded-xl bg-slate-900">
                <video ref={videoRef} muted playsInline autoPlay className="aspect-[4/3] w-full object-cover" />
                <div
                  className={`pointer-events-none absolute inset-[12%] rounded-2xl border-4 ${
                    state.kind === 'scanning' ? 'animate-pulse border-white/80' : 'border-white/30'
                  }`}
                />
                <span className="absolute left-2 top-2 rounded bg-black/60 px-2 py-0.5 text-xs font-semibold text-white">
                  {state.kind === 'scanning' ? '● Đang quét' : 'Tạm dừng quét'}
                </span>
              </div>
            )}
            {foreignCode && state.kind === 'scanning' && (
              <p className="text-sm text-amber-700">Mã vừa quét không phải mã QR gửi xe của hệ thống.</p>
            )}
            <form onSubmit={submitTyped} className="flex gap-2">
              <input
                value={typed}
                onChange={(event) => setTyped(event.target.value.toUpperCase())}
                placeholder="Không có QR? Gõ vài số cuối"
                aria-label="Gõ biển số khi không có mã QR"
                disabled={state.kind !== 'scanning'}
                className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 font-mono uppercase disabled:bg-slate-100"
              />
              <Button type="submit" variant="secondary" disabled={state.kind !== 'scanning'}>
                Tra cứu
              </Button>
            </form>
            {state.kind === 'scanning' && suggestions.length > 0 && (
              <ul className="grid grid-cols-2 gap-2" aria-label="Biển số gợi ý">
                {suggestions.map((suggestion) => (
                  <li key={suggestion.plateNumber}>
                    <button
                      type="button"
                      onClick={() => pick(suggestion.plateNumber)}
                      className="w-full rounded-xl border-2 border-slate-200 bg-slate-50 px-3 py-2 text-left hover:border-blue-500 hover:bg-blue-50"
                    >
                      <span className="block font-mono text-lg font-bold tracking-wider">{formatPlate(suggestion.plateNumber)}</span>
                      <span className="block text-xs text-slate-500">
                        {typeName(suggestion.vehicleTypeCode)}
                        {!flow.parkedOnly && suggestion.parked && (
                          <span className="ml-1 font-semibold text-amber-700">· đang trong bãi</span>
                        )}
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            {state.kind === 'scanning' && settled && suggestions.length === 0 && (
              <p className="text-sm text-slate-500">
                {flow.parkedOnly ? 'Không có xe nào đang trong bãi khớp với số vừa gõ.' : 'Không có biển số đã đăng ký nào khớp.'}
              </p>
            )}
          </div>
          <ResultPane state={state} flow={flow} typeName={typeName} dispatch={dispatch} />
        </div>
      </Card>

      {history.length > 0 && (
        <Card title={`Vừa xử lý (${flow.title.toLowerCase()})`}>
          <ul className="divide-y divide-slate-100">
            {history.map((item, index) => (
              <li key={`${item.at}-${index}`} className="flex items-center justify-between gap-3 py-2">
                <span className="font-mono text-lg font-bold">{item.plateDisplay}</span>
                <span className={`text-sm font-semibold ${item.ok ? 'text-emerald-700' : 'text-red-700'}`}>
                  {item.ok ? '✓' : '✗'} {item.text}
                </span>
                <span className="text-sm text-slate-500">{formatTime(toVietnamTime(item.at))}</span>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  );
}

interface ResultPaneProps {
  state: ScanState;
  flow: GateFlow;
  typeName: (code: string) => string;
  dispatch: (event: Parameters<typeof reduce>[1]) => void;
}

/** Khung kết quả to, đổi màu theo trạng thái — nhìn từ xa vẫn biết. */
function ResultPane({ state, flow, typeName, dispatch }: ResultPaneProps) {
  switch (state.kind) {
    case 'scanning':
      return (
        <Pane tone="idle" title="Sẵn sàng" subtitle="Đưa mã QR trên điện thoại sinh viên vào khung camera" />
      );
    case 'checking':
      return <Pane tone="idle" title="Đang tra cứu..." subtitle={state.plate} />;
    case 'countdown': {
      const seconds = Math.ceil(state.remainingMs / 1000);
      return (
        <Pane tone="allow" title={`✓ ${state.verdict.plateDisplay}`} verdict={state.verdict} typeName={typeName}>
          <div className="space-y-2">
            <p className="text-lg font-semibold">
              {flow.countdownLabel} {seconds} giây
            </p>
            <div className="h-2 overflow-hidden rounded-full bg-white/30">
              <div className="h-full bg-white transition-[width] duration-100" style={{ width: `${(state.remainingMs / COUNTDOWN_MS) * 100}%` }} />
            </div>
            <div className="flex gap-2">
              <Button variant="danger" size="xl" className="flex-1" onClick={() => dispatch({ type: 'STOP' })}>
                Dừng
              </Button>
              <Button variant="secondary" size="xl" onClick={() => dispatch({ type: 'CONFIRM_NOW' })}>
                Xác nhận ngay
              </Button>
            </div>
          </div>
        </Pane>
      );
    }
    case 'confirming':
      return <Pane tone="allow" title="Đang xác nhận..." verdict={state.verdict} typeName={typeName} />;
    case 'done':
      return (
        <Pane tone="done" title={`✓ ${state.outcome.title}`} subtitle={state.verdict.plateDisplay}>
          <dl className="space-y-1 text-lg">
            {state.outcome.lines.map(([label, value]) => (
              <div key={label} className="flex justify-between gap-3">
                <dt>{label}</dt>
                <dd className="font-bold">{value}</dd>
              </div>
            ))}
          </dl>
          {state.outcome.notice && <p className="mt-2 rounded bg-white/20 px-2 py-1">{state.outcome.notice}</p>}
          <NextButton dispatch={dispatch} />
        </Pane>
      );
    case 'denied':
      return (
        <Pane tone="deny" title={`✗ ${reasonText(state.verdict)}`} verdict={state.verdict} typeName={typeName}>
          {state.verdict.reason === 'INSUFFICIENT_POINTS' && state.verdict.shortfall !== null && state.verdict.shortfall > 0 && (
            <p className="text-base">Sinh viên cần đổi thêm ít nhất {formatCash(state.verdict.shortfall)} tại quầy quản lý.</p>
          )}
          <NextButton dispatch={dispatch} />
        </Pane>
      );
    case 'stopped':
      return (
        <Pane tone="deny" title="Đã dừng — chưa cho qua" verdict={state.verdict} typeName={typeName}>
          <p>Không có gì được ghi nhận cho xe này.</p>
          <NextButton dispatch={dispatch} />
        </Pane>
      );
    case 'failed':
      return (
        <Pane tone="deny" title="Có lỗi" subtitle={state.verdict?.plateDisplay ?? state.plate}>
          <p className="text-lg">{state.message}</p>
          <div className="mt-3 flex gap-2">
            {state.key && (
              <Button variant="secondary" size="lg" className="flex-1" onClick={() => dispatch({ type: 'RETRY' })}>
                Thử lại
              </Button>
            )}
            <Button variant="secondary" size="lg" className="flex-1" onClick={() => dispatch({ type: 'NEXT' })}>
              Bỏ qua, quét xe khác
            </Button>
          </div>
        </Pane>
      );
  }
}

const PANE_TONES = {
  idle: 'bg-slate-100 text-slate-700',
  allow: 'bg-emerald-600 text-white',
  done: 'bg-blue-700 text-white',
  deny: 'bg-red-600 text-white',
} as const;

interface PaneProps {
  tone: keyof typeof PANE_TONES;
  title: string;
  subtitle?: string;
  verdict?: Verdict;
  typeName?: (code: string) => string;
  children?: ReactNode;
}

function Pane({ tone, title, subtitle, verdict, typeName, children }: PaneProps) {
  return (
    <section className={`flex min-h-64 flex-col justify-center gap-3 rounded-xl p-5 ${PANE_TONES[tone]}`} aria-live="assertive">
      <p className="text-3xl font-bold">{title}</p>
      {subtitle && <p className="font-mono text-xl">{subtitle}</p>}
      {verdict?.fullName && (
        <div className="text-xl">
          <p className="font-bold">{verdict.fullName}</p>
          <p>
            MSSV {verdict.studentCode}
            {verdict.vehicleTypeCode && typeName && <> · {typeName(verdict.vehicleTypeCode)}</>}
          </p>
          {verdict.detail && <p>{verdict.detail}</p>}
        </div>
      )}
      {children}
    </section>
  );
}

function NextButton({ dispatch }: { dispatch: ResultPaneProps['dispatch'] }) {
  return (
    <Button variant="secondary" size="lg" className="mt-3 w-full" onClick={() => dispatch({ type: 'NEXT' })}>
      Quét xe tiếp theo
    </Button>
  );
}

function reasonText(verdict: Verdict): string {
  if (verdict.reason === 'INSUFFICIENT_POINTS' && verdict.shortfall !== null && verdict.shortfall > 0) {
    return `Thiếu ${formatPoints(verdict.shortfall)}`;
  }
  return verdict.reason ? (REASON_LABELS[verdict.reason] ?? verdict.reason) : 'Không cho qua';
}

/** Giờ máy -> chuỗi giờ Việt Nam để dùng chung formatTime. */
function toVietnamTime(iso: string): string {
  return new Date(new Date(iso).getTime() + 7 * 3600_000).toISOString().replace('Z', '+07:00');
}
