import { z } from 'zod';

export const MAX_AMOUNT = 10_000_000;

/** "50.000" / "50 000" / "50000" -> 50000; không phải số nguyên dương -> null. */
export function parseAmount(raw: string): number | null {
  const digits = raw.replace(/[\s.,]/g, '');
  if (!/^\d+$/.test(digits)) return null;
  const value = Number(digits);
  return Number.isSafeInteger(value) ? value : null;
}

/**
 * Ô nhập số tiền/số điểm (giữ dạng chuỗi trong form, đổi sang số bằng {@link parseAmount} khi gửi).
 * Kiểm tra sơ bộ cho tiện — backend vẫn kiểm tra lại (INVALID_AMOUNT, VALIDATION_ERROR).
 */
export function amountSchema(unit: 'tiền' | 'điểm') {
  return z
    .string()
    .min(1, `Vui lòng nhập số ${unit}`)
    .superRefine((raw, context) => {
      const value = parseAmount(raw);
      if (value === null || value <= 0) {
        context.addIssue({ code: z.ZodIssueCode.custom, message: `Số ${unit} phải là số nguyên dương` });
      } else if (value > MAX_AMOUNT) {
        context.addIssue({ code: z.ZodIssueCode.custom, message: `Tối đa 10.000.000 ${unit === 'tiền' ? 'đ' : 'điểm'} mỗi lần` });
      }
    });
}
