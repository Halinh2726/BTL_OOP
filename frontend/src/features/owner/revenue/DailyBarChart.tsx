import { useState, type KeyboardEvent, type PointerEvent } from 'react';
import { useElementWidth } from '../../../shared/hooks/useElementWidth';
import { axisScale, labelEvery, longDate, shortDate, tickLabel } from './revenueMath';

/** Một chuỗi số liệu theo ngày. `down` vẽ cột quay xuống dưới đường 0 (ví dụ tiền ra) — vẫn chung MỘT trục. */
export interface BarSeries {
  id: string;
  label: string;
  color: string;
  direction: 'up' | 'down';
  values: number[];
  format: (value: number) => string;
}

export interface DetailRow {
  label: string;
  value: string;
}

interface DailyBarChartProps {
  dates: string[];
  series: BarSeries[];
  /** Mô tả ngắn cho trình đọc màn hình. */
  ariaLabel: string;
  height?: number;
  /** Chuỗi được ghi số trực tiếp trên cột cao nhất (chỉ một nhãn — phần còn lại xem bằng rê chuột / bảng). */
  labelMaxOf?: string;
  /** Các dòng phụ trong ô chi tiết của một ngày (ví dụ số lượt xe ra). */
  details?: (index: number) => DetailRow[];
}

const INK = { secondary: '#52514e', muted: '#898781', grid: '#e1e0d9', baseline: '#c3c2b7', band: '#eef2f7' };
const MARGIN = { top: 22, right: 8, bottom: 26, left: 46 };
const MAX_BAR = 24;
const RADIUS = 4;
const TOOLTIP_WIDTH = 200;

/** Cột có đầu dữ liệu bo 4px, chân (phía đường 0) vuông. */
function barPath(x: number, width: number, base: number, end: number): string {
  const up = end < base;
  const r = Math.min(RADIUS, width / 2, Math.abs(base - end));
  const edge = up ? end + r : end - r;
  return `M${x},${base}V${edge}Q${x},${end} ${x + r},${end}H${x + width - r}Q${x + width},${end} ${x + width},${edge}V${base}Z`;
}

/**
 * Biểu đồ cột theo ngày, tự vẽ bằng SVG (không cần thư viện). Một trục y duy nhất; chuỗi `up` mọc lên, chuỗi `down`
 * mọc xuống từ cùng đường 0. Rê chuột hoặc dùng phím ←/→ khi biểu đồ đang được chọn để xem chi tiết từng ngày.
 */
export function DailyBarChart({ dates, series, ariaLabel, height = 220, labelMaxOf, details }: DailyBarChartProps) {
  const { ref, width } = useElementWidth<HTMLDivElement>();
  const [active, setActive] = useState<number | null>(null);

  const maxOf = (direction: BarSeries['direction']) =>
    Math.max(0, ...series.filter((s) => s.direction === direction).flatMap((s) => s.values));
  const scale = axisScale(maxOf('up'), maxOf('down'));

  const plotWidth = Math.max(1, width - MARGIN.left - MARGIN.right);
  const plotHeight = height - MARGIN.top - MARGIN.bottom;
  const y = (value: number) => MARGIN.top + ((scale.max - value) / (scale.max - scale.min)) * plotHeight;
  const base = y(0);
  const slot = plotWidth / Math.max(1, dates.length);
  const barWidth = Math.max(1, Math.min(MAX_BAR, slot - 2, slot * 0.62));
  const center = (index: number) => MARGIN.left + slot * index + slot / 2;
  const every = labelEvery(slot);

  const labelled = series.find((s) => s.id === labelMaxOf);
  const peakValue = labelled ? Math.max(0, ...labelled.values) : 0;
  const peak = labelled && peakValue > 0 ? labelled.values.indexOf(peakValue) : -1;

  function indexAt(event: PointerEvent<SVGRectElement>): number {
    const box = event.currentTarget.getBoundingClientRect();
    return Math.min(dates.length - 1, Math.max(0, Math.floor((event.clientX - box.left) / slot)));
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight' && event.key !== 'Home' && event.key !== 'End') return;
    event.preventDefault();
    const last = dates.length - 1;
    setActive((current) => {
      if (event.key === 'Home') return 0;
      if (event.key === 'End') return last;
      const start = current ?? last;
      return Math.min(last, Math.max(0, start + (event.key === 'ArrowRight' ? 1 : -1)));
    });
  }

  // Ô chi tiết nằm cạnh cột đang xem (bên phải nếu cột ở nửa trái và ngược lại) để không che chính cột đó
  const tooltipLeft =
    active === null
      ? 0
      : Math.min(
          Math.max(0, center(active) < width / 2 ? center(active) + slot / 2 + 8 : center(active) - slot / 2 - 8 - TOOLTIP_WIDTH),
          Math.max(0, width - TOOLTIP_WIDTH),
        );

  return (
    <div
      ref={ref}
      className="relative rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
      tabIndex={0}
      role="group"
      aria-label={`${ariaLabel}. Dùng phím mũi tên trái phải để xem từng ngày.`}
      onKeyDown={onKeyDown}
      onFocus={() => setActive((current) => current ?? dates.length - 1)}
      onBlur={() => setActive(null)}
    >
      <svg width={width} height={height} role="img" aria-label={ariaLabel} className="block select-none">
        {scale.ticks.map((tick) => (
          <g key={tick}>
            <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(tick)} y2={y(tick)} stroke={tick === 0 ? INK.baseline : INK.grid} strokeWidth={1} shapeRendering="crispEdges" />
            <text x={MARGIN.left - 6} y={y(tick)} dy="0.32em" textAnchor="end" fontSize={11} fill={INK.muted} style={{ fontVariantNumeric: 'tabular-nums' }}>
              {/* Phần dưới đường 0 là chuỗi "down" (ví dụ tiền ra): in độ lớn, không in dấu trừ */}
              {tickLabel(Math.abs(tick), Math.max(scale.max, -scale.min))}
            </text>
          </g>
        ))}

        {active !== null && <rect x={MARGIN.left + slot * active} y={MARGIN.top} width={slot} height={plotHeight} fill={INK.band} />}

        {series.map((s) =>
          s.values.map((value, index) => {
            if (value <= 0) return null;
            const end = y(s.direction === 'up' ? value : -value);
            const visibleEnd = Math.abs(end - base) < 1.5 ? base + (s.direction === 'up' ? -1.5 : 1.5) : end;
            return <path key={`${s.id}-${index}`} d={barPath(center(index) - barWidth / 2, barWidth, base, visibleEnd)} fill={s.color} />;
          }),
        )}

        {labelled && peak >= 0 && (
          // Cột sát mép: canh chữ về phía trong để không bị cắt
          <text
            x={center(peak) > width - 60 ? center(peak) + barWidth / 2 : center(peak) < MARGIN.left + 40 ? center(peak) - barWidth / 2 : center(peak)}
            y={y(peakValue) - 6}
            textAnchor={center(peak) > width - 60 ? 'end' : center(peak) < MARGIN.left + 40 ? 'start' : 'middle'}
            fontSize={11}
            fontWeight={600}
            fill={INK.secondary}
          >
            {labelled.format(peakValue)}
          </text>
        )}

        {dates.map((date, index) =>
          (dates.length - 1 - index) % every === 0 ? (
            <text key={date} x={center(index)} y={height - 8} textAnchor="middle" fontSize={11} fill={INK.muted} style={{ fontVariantNumeric: 'tabular-nums' }}>
              {shortDate(date)}
            </text>
          ) : null,
        )}

        <rect
          x={MARGIN.left}
          y={MARGIN.top}
          width={plotWidth}
          height={plotHeight}
          fill="transparent"
          onPointerMove={(event) => setActive(indexAt(event))}
          onPointerLeave={() => setActive(null)}
        />
      </svg>

      {active !== null && dates[active] && (
        <div
          role="status"
          className="pointer-events-none absolute top-0 z-10 rounded-lg bg-white p-3 text-sm shadow-lg ring-1 ring-slate-200"
          style={{ left: tooltipLeft, width: TOOLTIP_WIDTH }}
        >
          <p className="mb-1 text-xs font-medium text-slate-500">{longDate(dates[active])}</p>
          <ul className="space-y-0.5">
            {series.map((s) => (
              <li key={s.id} className="flex items-center gap-2">
                <span className="h-0.5 w-3 shrink-0 rounded" style={{ backgroundColor: s.color }} aria-hidden="true" />
                <span className="font-semibold text-slate-900">{s.format(s.values[active] ?? 0)}</span>
                <span className="truncate text-slate-500">{s.label}</span>
              </li>
            ))}
            {details?.(active).map((row) => (
              <li key={row.label} className="flex items-center gap-2 pl-5">
                <span className="font-semibold text-slate-900">{row.value}</span>
                <span className="truncate text-slate-500">{row.label}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

/** Chú thích: ô vuông màu (giống cột) + tên chuỗi. Bắt buộc khi có từ 2 chuỗi trở lên. */
export function ChartLegend({ items }: { items: Array<{ label: string; color: string }> }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-600">
      {items.map((item) => (
        <li key={item.label} className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ backgroundColor: item.color }} aria-hidden="true" />
          {item.label}
        </li>
      ))}
    </ul>
  );
}
