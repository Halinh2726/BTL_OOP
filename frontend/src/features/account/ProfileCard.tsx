import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { api, errorMessage, isApiError } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card, InfoRow } from '../../shared/components/Card';
import { TextField } from '../../shared/components/Field';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { useFieldErrors } from '../../shared/hooks/useFieldErrors';
import type { Profile } from '../../shared/types/api';
import { ROLE_LABELS } from '../../shared/utils/format';
import { useSession } from '../auth/session';

const schema = z.object({
  fullName: z.string().trim().min(1, 'Vui lòng nhập họ tên').max(100, 'Họ tên tối đa 100 ký tự'),
  email: z
    .string()
    .trim()
    .max(100, 'Email tối đa 100 ký tự')
    .refine((value) => value === '' || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(value), 'Email không đúng định dạng'),
  phone: z
    .string()
    .transform((value) => value.replace(/[\s.-]/g, ''))
    .refine((value) => value === '' || /^0[0-9]{9,10}$/.test(value), 'Số điện thoại gồm 10–11 chữ số, bắt đầu bằng 0'),
});

type Input = z.input<typeof schema>;
type Output = z.output<typeof schema>;
const FIELDS = ['fullName', 'email', 'phone'] as const;

/**
 * Thông tin cá nhân (mọi vai trò). Sửa được họ tên, email (nhận mã khi quên mật khẩu), số điện thoại;
 * tên đăng nhập và MSSV chỉ hiển thị — là căn cứ đối chiếu thẻ và truy vết, không sửa được.
 */
export function ProfileCard() {
  const profile = useQuery({ queryKey: queryKeys.myProfile, queryFn: api.myProfile });

  return (
    <Card title="Thông tin cá nhân">
      {profile.isPending && <LoadingBlock />}
      {profile.isError && <QueryError error={profile.error} onRetry={() => void profile.refetch()} />}
      {profile.data && <ProfileForm profile={profile.data} />}
    </Card>
  );
}

function ProfileForm({ profile }: { profile: Profile }) {
  const queryClient = useQueryClient();
  const { retry } = useSession();
  const [saved, setSaved] = useState(false);
  const form = useForm<Input, unknown, Output>({ resolver: zodResolver(schema), defaultValues: toForm(profile) });
  const applyFieldErrors = useFieldErrors(form.setError, FIELDS);

  useEffect(() => form.reset(toForm(profile)), [form, profile]);

  const update = useMutation({
    mutationFn: api.updateMyProfile,
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.myProfile, updated);
      setSaved(true);
      // Họ tên hiển thị trên thanh tiêu đề lấy từ GET /me: đổi tên thì tải lại phiên
      if (updated.fullName !== profile.fullName) retry();
    },
    onError: (error) => {
      if (applyFieldErrors(error)) return;
      if (isApiError(error) && error.code === 'EMAIL_EXISTS') {
        form.setError('email', { type: 'server', message: error.message });
      }
    },
  });

  const { errors } = form.formState;
  const handled = isApiError(update.error) && ['VALIDATION_ERROR', 'EMAIL_EXISTS'].includes(update.error.code);

  return (
    <div className="space-y-5">
      <dl className="text-sm">
        <InfoRow label="Tên đăng nhập" value={<span className="font-mono">{profile.username}</span>} />
        <InfoRow label="Vai trò" value={ROLE_LABELS[profile.role]} />
        {profile.studentCode && <InfoRow label="MSSV" value={<span className="font-mono">{profile.studentCode}</span>} />}
      </dl>
      <form
        className="space-y-4"
        noValidate
        onChange={() => setSaved(false)}
        onSubmit={form.handleSubmit((values) => update.mutate(values))}
      >
        {saved && <Alert tone="success">Đã lưu thông tin cá nhân.</Alert>}
        {update.isError && !handled && <Alert tone="error">{errorMessage(update.error)}</Alert>}
        <TextField label="Họ tên" autoComplete="name" error={errors.fullName?.message} {...form.register('fullName')} />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          placeholder="ten@ptit.edu.vn"
          hint="Dùng để nhận mã khi quên mật khẩu. Để trống nếu không muốn khai báo."
          error={errors.email?.message}
          {...form.register('email')}
        />
        {profile.phoneEditable && (
          <TextField
            label={profile.phoneRequired ? 'Số điện thoại (bắt buộc)' : 'Số điện thoại'}
            type="tel"
            autoComplete="tel"
            placeholder="0912345678"
            error={errors.phone?.message}
            {...form.register('phone')}
          />
        )}
        <Button type="submit" loading={update.isPending}>
          Lưu thông tin
        </Button>
      </form>
    </div>
  );
}

function toForm(profile: Profile): Input {
  return { fullName: profile.fullName, email: profile.email ?? '', phone: profile.phone ?? '' };
}
