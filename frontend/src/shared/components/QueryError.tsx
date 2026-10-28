import { errorMessage } from '../api';
import { Alert } from './Alert';
import { Button } from './Button';

/** Lỗi khi tải dữ liệu, kèm nút thử lại. */
export function QueryError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <Alert tone="error">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span>{errorMessage(error)}</span>
        {onRetry && (
          <Button size="sm" variant="secondary" onClick={onRetry}>
            Thử lại
          </Button>
        )}
      </div>
    </Alert>
  );
}
