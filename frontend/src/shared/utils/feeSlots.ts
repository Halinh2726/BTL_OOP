import type { FeeRule } from '../types/api';

export const DAY_SLOT_MINUTES = 1080; // Trước 18:00
export const EVENING_SLOT_MINUTES = 1320; // Từ 18:00 đến trước 22:00
export const OVERNIGHT_SLOT_MINUTES = 1440; // Qua đêm (sau 22:00 / mỗi đêm)

export interface TimeSlotDefinition {
  upToMinutes: number;
  label: string;
  shortLabel: string;
  description: string;
  unitSuffix: string;
  defaultPoints: number;
  highlight?: boolean;
}

export const FIXED_TIME_SLOTS: readonly TimeSlotDefinition[] = [
  {
    upToMinutes: DAY_SLOT_MINUTES,
    label: 'Trước 18:00 (ban ngày)',
    shortLabel: 'Trước 18:00',
    description: 'Lấy xe trước 18h chiều trong ngày',
    unitSuffix: 'điểm',
    defaultPoints: 3000,
  },
  {
    upToMinutes: EVENING_SLOT_MINUTES,
    label: 'Từ 18:00 đến 22:00 (buổi tối)',
    shortLabel: '18:00 – 22:00',
    description: 'Lấy xe từ 18h chiều đến trước 22h tối',
    unitSuffix: 'điểm',
    defaultPoints: 5000,
  },
  {
    upToMinutes: OVERNIGHT_SLOT_MINUTES,
    label: 'Qua đêm (sau 22:00 / mỗi đêm)',
    shortLabel: 'Qua đêm / đêm',
    description: 'Mỗi đêm để qua ngày, cộng dồn với khung giờ ngày lấy xe',
    unitSuffix: 'điểm / đêm',
    defaultPoints: 15000,
    highlight: true,
  },
] as const;

export function slotMeta(upToMinutes: number): TimeSlotDefinition {
  return (
    FIXED_TIME_SLOTS.find((slot) => slot.upToMinutes === upToMinutes) ??
    FIXED_TIME_SLOTS[FIXED_TIME_SLOTS.length - 1]!
  );
}

/** Chuẩn hóa mảng FeeRule về đúng 3 khung giờ cố định (1080, 1320, 1440). */
export function toFixedTimeSlotRules(rules: FeeRule[]): FeeRule[] {
  const byMinutes = new Map(rules.map((r) => [r.upToMinutes, r.feePoints]));
  return FIXED_TIME_SLOTS.map((slot, index) => ({
    upToMinutes: slot.upToMinutes,
    feePoints: byMinutes.get(slot.upToMinutes) ?? rules[index]?.feePoints ?? slot.defaultPoints,
  }));
}
