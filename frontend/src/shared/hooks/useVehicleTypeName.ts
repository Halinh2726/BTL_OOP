import { useQuery } from '@tanstack/react-query';
import { useCallback } from 'react';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';

/** Đổi mã loại xe (XE_MAY) sang tên hiển thị (Xe máy) theo danh mục của backend. */
export function useVehicleTypeName(): (code: string | null) => string {
  const types = useQuery({ queryKey: queryKeys.vehicleTypes, queryFn: api.vehicleTypes, staleTime: Infinity });
  return useCallback(
    (code: string | null) => {
      if (!code) return '—';
      return types.data?.find((type) => type.code === code)?.name ?? code;
    },
    [types.data],
  );
}
