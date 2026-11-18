import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { api, errorMessage } from '../../shared/api';
import { queryKeys } from '../../shared/api/queryKeys';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { SelectField } from '../../shared/components/Field';
import type { Vehicle, VehicleType } from '../../shared/types/api';

interface ChangeVehicleTypeDialogProps {
  vehicle: Vehicle;
  types: VehicleType[];
  onClose: () => void;
  onChanged: (vehicle: Vehicle) => void;
}

/** Sửa loại xe (chọn nhầm lúc đăng ký). Biển số không sửa được — đổi biển là đăng ký xe mới. */
export function ChangeVehicleTypeDialog({ vehicle, types, onClose, onChanged }: ChangeVehicleTypeDialogProps) {
  const queryClient = useQueryClient();
  const [typeCode, setTypeCode] = useState(vehicle.vehicleTypeCode);

  const change = useMutation({
    mutationFn: () => api.changeVehicleType(vehicle.id, typeCode),
    onSuccess: async (changed) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.myVehicles });
      onChanged(changed);
    },
  });

  return (
    <Dialog
      title={`Sửa loại xe ${vehicle.plateDisplay}`}
      onClose={onClose}
      busy={change.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={change.isPending}>
            Hủy
          </Button>
          <Button onClick={() => change.mutate()} loading={change.isPending} disabled={typeCode === vehicle.vehicleTypeCode}>
            Lưu
          </Button>
        </>
      }
    >
      {change.isError && <Alert tone="error">{errorMessage(change.error)}</Alert>}
      <SelectField label="Loại xe" value={typeCode} onChange={(event) => setTypeCode(event.target.value)}>
        {types.map((type) => (
          <option key={type.code} value={type.code}>
            {type.name}
          </option>
        ))}
      </SelectField>
    </Dialog>
  );
}
