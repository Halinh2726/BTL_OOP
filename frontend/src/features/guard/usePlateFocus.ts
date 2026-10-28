import { useCallback, useRef, useState } from 'react';

/**
 * "Xe tiếp theo": dựng lại ô biển số (key mới -> ô trống) và đặt con trỏ vào đó để gõ ngay.
 */
export function usePlateFocus() {
  const inputRef = useRef<HTMLInputElement>(null);
  const [formKey, setFormKey] = useState(0);
  const resetForm = useCallback(() => {
    setFormKey((key) => key + 1);
    requestAnimationFrame(() => inputRef.current?.focus());
  }, []);
  return { inputRef, formKey, resetForm };
}
