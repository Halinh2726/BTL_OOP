import type { ReactNode } from 'react';
import { Alert } from '../../shared/components/Alert';
import { Button } from '../../shared/components/Button';
import { Card } from '../../shared/components/Card';

interface ReceiptProps {
  title: string;
  replayed: boolean;
  children: ReactNode;
  onDone: () => void;
}

/** Biên nhận sau giao dịch: số liệu lấy NGUYÊN từ response của backend. */
export function Receipt({ title, replayed, children, onDone }: ReceiptProps) {
  return (
    <Card className="ring-2 ring-emerald-500">
      <div className="space-y-4">
        <h2 className="text-xl font-bold text-emerald-800">✓ {title}</h2>
        {replayed && <Alert tone="info">Giao dịch này đã được ghi nhận trước đó — không cộng/trừ điểm lần hai.</Alert>}
        <dl>{children}</dl>
        <Button onClick={onDone} autoFocus>
          Giao dịch mới
        </Button>
      </div>
    </Card>
  );
}
