import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { api } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import { formatDateTime } from '../../shared/utils/format';
import { RegisterVehicleDialog } from './RegisterVehicleDialog';

/** "Xe của tôi" (GET /me/vehicles) + nút đăng ký xe mới. */
export function VehiclesCard() {
  const [registering, setRegistering] = useState(false);
  const vehicles = useQuery({ queryKey: queryKeys.myVehicles, queryFn: api.myVehicles });
  const types = useQuery({ queryKey: queryKeys.vehicleTypes, queryFn: api.vehicleTypes, staleTime: Infinity });
  const typeName = (code: string) => types.data?.find((type) => type.code === code)?.name ?? code;

  return (
    <Card
      title="Xe của tôi"
      actions={
        <Button size="sm" onClick={() => setRegistering(true)}>
          + Đăng ký xe
        </Button>
      }
    >
      {vehicles.isPending && <LoadingBlock />}
      {vehicles.isError && <QueryError error={vehicles.error} onRetry={() => void vehicles.refetch()} />}
      {vehicles.data?.length === 0 && (
        <p className="py-6 text-center text-slate-500">Bạn chưa đăng ký xe nào. Đăng ký biển số để gửi xe bằng điểm.</p>
      )}
      {vehicles.data && vehicles.data.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-2">
          {vehicles.data.map((vehicle) => (
            <li key={vehicle.id} className="rounded-lg border border-slate-200 p-4">
              <p className="font-mono text-xl font-bold tracking-wider text-slate-900">{vehicle.plateNumber}</p>
              <p className="mt-1 text-sm text-slate-600">{typeName(vehicle.vehicleTypeCode)}</p>
              <p className="mt-1 text-xs text-slate-400">Đăng ký lúc {formatDateTime(vehicle.registeredAt)}</p>
            </li>
          ))}
        </ul>
      )}
      {registering && <RegisterVehicleDialog types={types.data ?? []} onClose={() => setRegistering(false)} />}
    </Card>
  );
}
