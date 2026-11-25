import { Button } from '../../shared/components/Button';

export function GateCustomerMode({ guest, onChange }: { guest: boolean; onChange: (guest: boolean) => void }) {
  return (
    <div className="mb-3 flex gap-2" role="group" aria-label="Đối tượng gửi xe">
      <Button size="sm" variant={guest ? 'secondary' : 'primary'} aria-pressed={!guest} onClick={() => onChange(false)}>
        Sinh viên
      </Button>
      <Button size="sm" variant={guest ? 'primary' : 'secondary'} aria-pressed={guest} onClick={() => onChange(true)}>
        Khách
      </Button>
    </div>
  );
}
