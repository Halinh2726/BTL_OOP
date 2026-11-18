import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';
import { ConfirmDialog } from '../../shared/components/Dialog';
import { QueryError } from '../../shared/components/QueryError';
import { LoadingBlock } from '../../shared/components/Spinner';
import type { Vehicle } from '../../shared/types/api';
import { VEHICLE_STATUS_LABELS, formatDateTime } from '../../shared/utils/format';
import { ChangeVehicleTypeDialog } from './ChangeVehicleTypeDialog';
import { RegisterVehicleDialog } from './RegisterVehicleDialog';
import { VehicleQrDialog } from './VehicleQrDialog';

/** Tối đa 3 xe ĐANG DÙNG cho mỗi sinh viên (cùng Vehicle.MAX_ACTIVE_PER_STUDENT của backend). */
export const MAX_ACTIVE_VEHICLES = 3;

/** Ngừng xe cũ: chỉ ngừng, hoặc ngừng rồi mở ngay form đăng ký xe mới ("đổi xe"). */
interface Deactivation {
  vehicle: Vehicle;
  thenRegister: boolean;
}

/**
 * "Xe của tôi" (GET /me/vehicles). Đợt 1: sửa loại xe, ngừng sử dụng ("xóa xe" — vẫn giữ lịch sử lượt gửi),
 * đổi xe (= ngừng xe cũ + đăng ký xe mới), tối đa 3 xe đang dùng, biển số hiển thị theo quy định.
 */
export function VehiclesCard() {
  const queryClient = useQueryClient();
  const [registering, setRegistering] = useState(false);
  const [qrVehicle, setQrVehicle] = useState<Vehicle | null>(null);
  const [editing, setEditing] = useState<Vehicle | null>(null);
  const [deactivation, setDeactivation] = useState<Deactivation | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const vehicles = useQuery({ queryKey: queryKeys.myVehicles, queryFn: api.myVehicles });
  const types = useQuery({ queryKey: queryKeys.vehicleTypes, queryFn: api.vehicleTypes, staleTime: Infinity });
  const typeName = (code: string) => types.data?.find((type) => type.code === code)?.name ?? code;

  const activeCount = vehicles.data?.filter((vehicle) => vehicle.status === 'DANG_DUNG').length ?? 0;
  const full = activeCount >= MAX_ACTIVE_VEHICLES;

  const deactivate = useMutation({
    mutationFn: (target: Deactivation) => api.deactivateVehicle(target.vehicle.id),
    onSuccess: async (stopped, target) => {
      setDeactivation(null);
      setMessage(`Đã ngừng sử dụng xe ${stopped.plateDisplay}. Lịch sử gửi xe vẫn được giữ.`);
      await queryClient.invalidateQueries({ queryKey: queryKeys.myVehicles });
      if (target.thenRegister) setRegistering(true);
    },
  });

  return (
    <Card
      title={
        <>
          Xe của tôi{' '}
          <span className="text-sm font-normal text-slate-500">
            ({activeCount}/{MAX_ACTIVE_VEHICLES} xe đang dùng)
          </span>
        </>
      }
      actions={
        <Button
          size="sm"
          disabled={full}
          title={full ? 'Đã đủ 3 xe đang dùng — ngừng hoặc đổi một xe trước' : undefined}
          onClick={() => {
            setMessage(null);
            setRegistering(true);
          }}
        >
          + Đăng ký xe
        </Button>
      }
    >
      {message && (
        <div className="mb-4">
          <Alert tone="success">{message}</Alert>
        </div>
      )}
      {vehicles.isPending && <LoadingBlock />}
      {vehicles.isError && <QueryError error={vehicles.error} onRetry={() => void vehicles.refetch()} />}
      {vehicles.data?.length === 0 && (
        <p className="py-6 text-center text-slate-500">Bạn chưa đăng ký xe nào. Đăng ký biển số để gửi xe bằng điểm.</p>
      )}
      {vehicles.data && vehicles.data.length > 0 && (
        <ul className="grid gap-3 sm:grid-cols-2">
          {vehicles.data.map((vehicle) => {
            const active = vehicle.status === 'DANG_DUNG';
            return (
              <li
                key={vehicle.id}
                className={`rounded-lg border p-4 ${active ? 'border-slate-200' : 'border-dashed border-slate-300 bg-slate-50'}`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className={`font-mono text-xl font-bold tracking-wider ${active ? 'text-slate-900' : 'text-slate-400 line-through'}`}>
                      {vehicle.plateDisplay}
                    </p>
                    <p className="mt-1 text-sm text-slate-600">{typeName(vehicle.vehicleTypeCode)}</p>
                  </div>
                  <Badge tone={active ? 'green' : 'gray'}>{VEHICLE_STATUS_LABELS[vehicle.status]}</Badge>
                </div>
                <p className="mt-1 text-xs text-slate-400">
                  Đăng ký lúc {formatDateTime(vehicle.registeredAt)}
                  {vehicle.deactivatedAt && <> · Ngừng lúc {formatDateTime(vehicle.deactivatedAt)}</>}
                </p>
                {active && (
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Button size="sm" variant="secondary" onClick={() => setQrVehicle(vehicle)}>
                      Mã QR
                    </Button>
                    <Button size="sm" variant="secondary" onClick={() => setEditing(vehicle)}>
                      Sửa loại xe
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={() => {
                        deactivate.reset();
                        setDeactivation({ vehicle, thenRegister: true });
                      }}
                    >
                      Đổi xe
                    </Button>
                    <Button
                      size="sm"
                      variant="ghost"
                      className="!text-red-700"
                      onClick={() => {
                        deactivate.reset();
                        setDeactivation({ vehicle, thenRegister: false });
                      }}
                    >
                      Ngừng sử dụng
                    </Button>
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}

      {deactivation && (
        <ConfirmDialog
          title={deactivation.thenRegister ? 'Đổi xe' : 'Ngừng sử dụng xe'}
          confirmLabel={deactivation.thenRegister ? 'Ngừng xe này và đăng ký xe mới' : 'Ngừng sử dụng'}
          tone="danger"
          pending={deactivate.isPending}
          onCancel={() => setDeactivation(null)}
          onConfirm={() => deactivate.mutate(deactivation)}
          error={deactivate.isError && <Alert tone="error">{errorMessage(deactivate.error)}</Alert>}
        >
          <p>
            Ngừng sử dụng xe <strong className="font-mono">{deactivation.vehicle.plateDisplay}</strong>?
          </p>
          <p className="text-sm text-slate-500">
            Bảo vệ sẽ không cho xe này vào bãi nữa. Lịch sử gửi xe vẫn được giữ; biển số được giải phóng để người mua lại
            xe có thể đăng ký. Không ngừng được khi xe đang trong bãi.
          </p>
        </ConfirmDialog>
      )}
      {editing && (
        <ChangeVehicleTypeDialog
          vehicle={editing}
          types={types.data ?? []}
          onClose={() => setEditing(null)}
          onChanged={(changed) => {
            setEditing(null);
            setMessage(`Đã đổi loại xe ${changed.plateDisplay} thành ${typeName(changed.vehicleTypeCode)}.`);
          }}
        />
      )}
      {qrVehicle && <VehicleQrDialog vehicle={qrVehicle} onClose={() => setQrVehicle(null)} />}
      {registering && <RegisterVehicleDialog types={types.data ?? []} onClose={() => setRegistering(false)} />}
    </Card>
  );
}
