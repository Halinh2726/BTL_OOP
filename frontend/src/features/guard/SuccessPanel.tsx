import type { ReactNode } from 'react';
import { Button } from '../../shared/components/Button';

interface SuccessPanelProps {
  title: string;
  children: ReactNode;
  onNext: () => void;
  notice?: ReactNode;
}

/** Màn hình kết quả sau khi xác nhận, với nút "Xe tiếp theo" to để quay lại ô biển số. */
export function SuccessPanel({ title, children, onNext, notice }: SuccessPanelProps) {
  return (
    <section className="space-y-5 rounded-xl bg-white p-6 shadow-sm ring-2 ring-emerald-500">
      <div className="text-center">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-emerald-100 text-4xl text-emerald-700">✓</div>
        <h2 className="mt-3 text-2xl font-bold text-emerald-800">{title}</h2>
      </div>
      {notice}
      <dl className="text-lg">{children}</dl>
      <Button size="xl" className="w-full" onClick={onNext} autoFocus>
        Xe tiếp theo
      </Button>
    </section>
  );
}
