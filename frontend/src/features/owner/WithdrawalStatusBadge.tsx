import { Badge } from '../../shared/components/Badge';
import type { WithdrawalStatus } from '../../shared/types/api';
import { WITHDRAWAL_STATUS_LABELS } from '../../shared/utils/format';

const TONES: Record<WithdrawalStatus, 'amber' | 'blue' | 'green' | 'gray' | 'red'> = {
  CHO_XU_LY: 'amber',
  DA_TRA: 'blue',
  DA_XAC_NHAN: 'green',
  TU_CHOI: 'gray',
  DA_HUY: 'gray',
  KHIEU_NAI: 'red',
};

export function WithdrawalStatusBadge({ status }: { status: WithdrawalStatus }) {
  return <Badge tone={TONES[status]}>{WITHDRAWAL_STATUS_LABELS[status]}</Badge>;
}
