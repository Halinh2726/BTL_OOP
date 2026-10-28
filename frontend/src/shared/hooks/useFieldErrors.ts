import { useCallback } from 'react';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { isApiError } from '../api';

/**
 * Lỗi VALIDATION_ERROR của backend có fields[] -> hiện dưới đúng ô nhập.
 * Trả về true khi đã gắn được ít nhất một lỗi vào form (khi đó không cần thông báo chung).
 */
export function useFieldErrors<T extends FieldValues>(setError: UseFormSetError<T>, fieldNames: readonly Path<T>[]) {
  return useCallback(
    (error: unknown): boolean => {
      if (!isApiError(error)) return false;
      let applied = false;
      error.fields.forEach((violation) => {
        const name = fieldNames.find((candidate) => candidate === violation.field);
        if (name) {
          setError(name, { type: 'server', message: violation.message });
          applied = true;
        }
      });
      return applied;
    },
    [setError, fieldNames],
  );
}
