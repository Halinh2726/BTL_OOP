import { z } from 'zod';

export const studentProfileSchema = z.object({
  studentCode: z
    .string()
    .trim()
    .min(1, 'Vui lòng nhập MSSV')
    .transform((value) => value.toUpperCase())
    .pipe(z.string().regex(/^[A-Z0-9]{1,20}$/, 'MSSV chỉ gồm chữ và số, tối đa 20 ký tự')),
  fullName: z.string().trim().min(1, 'Vui lòng nhập họ tên').max(100, 'Họ tên tối đa 100 ký tự'),
  username: z
    .string()
    .min(1, 'Vui lòng nhập tên đăng nhập')
    .regex(/^[a-z0-9._]{4,50}$/, 'Tên đăng nhập 4–50 ký tự, chỉ gồm chữ thường, số, dấu chấm, gạch dưới'),
});
